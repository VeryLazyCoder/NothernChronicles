import { createGame, applyCommand, botCommand, projectView } from './game.js';
import { BY_ID, ROWS } from './cards.js';
export class DemoTransport {
  constructor(onUpdate) { this.onUpdate = onUpdate; this.state = createGame(); this.status = 'demo'; this.pending = false; }
  emit() { this.onUpdate({ view: projectView(this.state), status: this.status, pending: false }); }
  start() { this.emit(); }
  send(type, payload = {}) {
    this.state = applyCommand(this.state, 'you', { type, payload }); this.emit(); this.scheduleBot();
  }
  scheduleBot() {
    clearTimeout(this.timer);
    if (this.state.phase === 'ROUND_OVER' && this.state.ready.includes('you') || this.state.phase === 'PLAYING' && this.state.activePlayerId === 'bot') {
      this.timer = setTimeout(() => { this.state = applyCommand(this.state, 'bot', botCommand(this.state)); this.emit(); this.scheduleBot(); }, 700);
    }
  }
  close() { clearTimeout(this.timer); }
}
// Проверяем границу сети до передачи данных рендереру. Сервер обязан проверять входящие команды сам.
export function validateView(v) {
  const fail = () => { throw Error('Сервер прислал несовместимый снимок. См. docs/PROTOCOL.md.'); };
  if (!v || !['PLAYING', 'ROUND_OVER', 'MATCH_OVER'].includes(v.phase) || !Number.isInteger(v.revision) || v.revision < 0 || typeof v.matchId !== 'string' || !Number.isInteger(v.round) || v.round < 1 || v.round > 3 || !Array.isArray(v.players) || v.players.length !== 2) fail();
  if (!Array.isArray(v.weather) || v.weather.some(r => !ROWS.includes(r)) || !Array.isArray(v.ready) || !Array.isArray(v.roundResults) || !Array.isArray(v.log) || v.log.some(x => typeof x !== 'string')) fail();
  const ids = v.players.map(p => p.id);
  if (ids.some(id => typeof id !== 'string') || new Set(ids).size !== 2 || !ids.includes(v.viewerId) || !(v.activePlayerId === null || ids.includes(v.activePlayerId)) || !(v.winnerId === null || ids.includes(v.winnerId))) fail();
  const checkCard = c => { if (!c || typeof c.instanceId !== 'string' || !Object.hasOwn(BY_ID, c.cardId)) fail(); };
  for (const p of v.players) {
    if (typeof p.name !== 'string' || typeof p.passed !== 'boolean' || !Number.isInteger(p.lives) || p.lives < 0 || p.lives > 2 || !p.board || !Array.isArray(p.horns)) fail();
    for (const key of ['handCount', 'deckCount', 'discardCount']) if (!Number.isInteger(p[key]) || p[key] < 0) fail();
    for (const r of ROWS) { if (!Array.isArray(p.board[r])) fail(); p.board[r].forEach(checkCard); }
    p.horns.forEach(h => { if (!ROWS.includes(h.row)) fail(); checkCard(h.card); });
    if (p.id === v.viewerId) { if (!Array.isArray(p.hand) || p.hand.length !== p.handCount) fail(); p.hand.forEach(checkCard); }
    else if ('hand' in p) fail();
    if ('deck' in p) fail();
  }
  for (const r of v.roundResults) if (!Array.isArray(r.scores) || r.scores.length !== 2 || r.scores.some(n => !Number.isInteger(n) || n < 0)) fail();
  return v;
}
export class WebSocketTransport {
  constructor({ url, nickname, onUpdate, onError }) {
    const u = new URL(url);
    if (!['ws:', 'wss:'].includes(u.protocol) || u.username || u.password || u.search || u.hash) throw Error('Укажите ws://host:port/ws или wss://host/ws без параметров.');
    if (location.protocol === 'https:' && u.protocol !== 'wss:') throw Error('Для HTTPS-страницы нужен защищённый адрес wss://.');
    this.url = u.href; this.apiOrigin = `${u.protocol === 'wss:' ? 'https:' : 'http:'}//${u.host}`;
    this.nickname = nickname; this.onUpdate = onUpdate; this.onError = onError;
    this.status = 'connecting'; this.attempt = 0; this.closed = false; this.view = null; this.room = null;
  }
  emit() { this.onUpdate({ view: this.view, room: this.room, status: this.status, pending: !!this.pending }); }
  async start() {
    this.emit();
    try {
      const response = await fetch(`${this.apiOrigin}/api/session`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nickname: this.nickname }), signal: AbortSignal.timeout(8000) });
      if (!response.ok) throw Error(`Не удалось создать сессию: HTTP ${response.status}.`);
      if (!this.closed) this.connect();
    } catch (e) { if (!this.closed) { this.status = 'offline'; this.emit(); this.onError(e.message); } }
  }
  connect() {
    if (this.closed) return;
    this.status = 'connecting'; this.emit();
    const socket = this.socket = new WebSocket(this.url);
    const connectTimeout = setTimeout(() => socket.close(), 8000);
    socket.onopen = () => {
      clearTimeout(connectTimeout); this.status = 'online'; this.emit();
      if (this.view || this.room) this.send('RESUME', { matchId: this.view?.matchId || null, roomId: this.room?.roomId || null });
    };
    socket.onmessage = event => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.protocolVersion !== 1) throw Error('Версия протокола сервера не поддерживается.');
        if (msg.type === 'SNAPSHOT') {
          const next = validateView(msg.payload);
          if (this.view && this.view.matchId === next.matchId && next.revision < this.view.revision) return;
          this.view = next; this.room = null; this.status = 'online'; this.attempt = 0;
        } else if (msg.type === 'ROOM_STATE') {
          if (!msg.payload || typeof msg.payload.code !== 'string' || typeof msg.payload.roomId !== 'string' || !Array.isArray(msg.payload.players) || msg.payload.players.some(p => typeof p.name !== 'string')) throw Error('Некорректное состояние комнаты.');
          this.room = msg.payload; this.status = 'online'; this.attempt = 0;
        } else if (msg.type === 'ERROR') {
          this.onError(`${msg.payload?.code || 'ERROR'}: ${msg.payload?.message || 'Сервер отклонил команду.'}`);
        } else throw Error('Неизвестное сообщение сервера.');
        if (msg.requestId === this.pending?.requestId || msg.type === 'SNAPSHOT' && this.pending?.type === 'RESUME') this.clearPending();
        this.emit();
      } catch (e) { this.status = 'incompatible'; this.clearPending(); this.emit(); this.onError(e.message); socket.close(1002, 'Protocol mismatch'); }
    };
    socket.onclose = () => {
      clearTimeout(connectTimeout); this.clearPending();
      if (this.closed || this.status === 'incompatible') return;
      this.status = 'offline'; this.emit();
      if (++this.attempt <= 5) this.retryTimer = setTimeout(() => this.connect(), Math.min(1000 * 2 ** (this.attempt - 1), 16000) + Math.random() * 300);
      else this.onError('Не удалось восстановить соединение. Откройте подключение и попробуйте снова.');
    };
    socket.onerror = () => this.onError('Ошибка WebSocket. Проверьте адрес, запуск Java-сервера и разрешённый Origin.');
  }
  clearPending() { clearTimeout(this.pendingTimer); this.pending = null; }
  send(type, payload = {}) {
    if (this.status !== 'online' || this.socket?.readyState !== WebSocket.OPEN) throw Error('Нет соединения с сервером.');
    if (this.pending) throw Error('Дождитесь ответа сервера.');
    const command = { protocolVersion: 1, requestId: crypto.randomUUID(), type, matchId: this.view?.matchId || null, expectedRevision: this.view?.revision ?? null, payload };
    this.socket.send(JSON.stringify(command)); this.pending = command;
    this.pendingTimer = setTimeout(() => {
      this.clearPending(); this.status = 'syncing'; this.emit();
      this.onError('Ответ не получен. Результат хода неизвестен; восстанавливаем состояние без повторной отправки хода.');
      this.socket.close();
    }, 8000);
    this.emit();
  }
  close() { this.closed = true; clearTimeout(this.retryTimer); this.clearPending(); this.socket?.close(1000, 'Client left'); }
}
