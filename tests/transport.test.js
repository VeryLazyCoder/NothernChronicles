import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocketTransport } from '../frontend/src/transport.js';
import { createGame, projectView } from '../frontend/src/game.js';

function harness(t) {
  const originals = { WebSocket: globalThis.WebSocket, location: globalThis.location, fetch: globalThis.fetch };
  let request;
  class Socket {
    static OPEN = 1;
    constructor(url) { this.url = url; this.readyState = 0; Socket.last = this; this.sent = []; }
    open() { this.readyState = 1; this.onopen(); }
    send(data) { this.sent.push(JSON.parse(data)); }
    message(type, payload, requestId = null) { this.onmessage({ data: JSON.stringify({ protocolVersion: 1, type, payload, requestId }) }); }
    close() { this.readyState = 3; this.onclose?.(); }
  }
  globalThis.WebSocket = Socket; globalThis.location = { protocol: 'http:' };
  globalThis.fetch = async (url, options) => { request = { url, ...options }; return { ok: true, status: 201 }; };
  const updates = [], errors = [];
  const tr = new WebSocketTransport({ url: 'ws://localhost:8080/ws', nickname: 'Путник', onUpdate: x => updates.push(x), onError: x => errors.push(x) });
  t.after(() => { tr.close(); Object.assign(globalThis, originals); });
  return { tr, updates, errors, Socket, get request() { return request; } };
}
test('сначала HTTP cookie-сессия, затем raw WebSocket; комната подтверждает requestId', async t => {
  const h = harness(t); await h.tr.start();
  assert.equal(h.request.url, 'http://localhost:8080/api/session'); assert.equal(h.request.credentials, 'include');
  assert.deepEqual(JSON.parse(h.request.body), { nickname: 'Путник' });
  h.Socket.last.open(); h.tr.send('CREATE_ROOM');
  const sent = h.Socket.last.sent[0]; assert.equal(sent.protocolVersion, 1); assert.equal(sent.matchId, null); assert.ok(sent.requestId);
  assert.throws(() => h.tr.send('CREATE_ROOM')); assert.equal(h.tr.pending.type, 'CREATE_ROOM');
  h.Socket.last.message('ROOM_STATE', { code: 'NORTH7', roomId: 'room-1', players: [{ name: 'Путник' }] }, sent.requestId);
  assert.equal(h.tr.pending, null); assert.equal(h.tr.room.code, 'NORTH7');
});
test('online ход не меняет поле до снимка; старая ревизия игнорируется', async t => {
  const h = harness(t); await h.tr.start(); const sock = h.Socket.last; sock.open();
  const v = projectView(createGame()); sock.message('SNAPSHOT', v);
  h.tr.send('PASS'); const command = sock.sent.at(-1);
  assert.equal(command.expectedRevision, 0); assert.equal(h.tr.view.players[0].passed, false);
  const next = structuredClone(v); next.revision = 1; next.players[0].passed = true; next.activePlayerId = 'bot';
  sock.message('SNAPSHOT', next, command.requestId); assert.equal(h.tr.pending, null); assert.equal(h.tr.view.revision, 1);
  sock.message('SNAPSHOT', v); assert.equal(h.tr.view.revision, 1);
});
test('серверный отказ снимает pending без изменения состояния', async t => {
  const h = harness(t); await h.tr.start(); const sock = h.Socket.last; sock.open();
  const v = projectView(createGame()); sock.message('SNAPSHOT', v); h.tr.send('PASS');
  sock.message('ERROR', { code: 'NOT_YOUR_TURN', message: 'Ход соперника.' }, sock.sent.at(-1).requestId);
  assert.equal(h.tr.pending, null); assert.deepEqual(h.tr.view, v); assert.match(h.errors[0], /NOT_YOUR_TURN/);
});
test('RESUME на новом сокете не повторяет неподтверждённую игровую команду', async t => {
  const h = harness(t); await h.tr.start(); const old = h.Socket.last; old.open();
  const v = projectView(createGame()); old.message('SNAPSHOT', v); h.tr.send('PASS');
  // Имитируем разрыв и запланированное переподключение, не ждём реальный backoff в unit-тесте.
  old.close(); clearTimeout(h.tr.retryTimer); h.tr.connect(); const next = h.Socket.last; next.open();
  assert.equal(next.sent.length, 1); assert.equal(next.sent[0].type, 'RESUME');
  assert.deepEqual(next.sent[0].payload, { matchId: 'demo', roomId: null });
  next.message('SNAPSHOT', v, next.sent[0].requestId); assert.equal(h.tr.pending, null);
});
test('несовместимый snapshot закрывает соединение и блокирует отправку', async t => {
  const h = harness(t); await h.tr.start(); const sock = h.Socket.last; sock.open();
  const v = projectView(createGame()); v.players[1].hand = [];
  sock.message('SNAPSHOT', v); assert.equal(h.tr.status, 'incompatible'); assert.equal(sock.readyState, 3);
  assert.equal(h.tr.view, null); assert.equal(h.errors.length, 1); assert.throws(() => h.tr.send('PASS'));
});
test('HTTPS-страница не подключается к ws; URL с credential/query отклоняется', t => {
  const h = harness(t); globalThis.location.protocol = 'https:';
  const config = { nickname: 'A', onUpdate() {}, onError() {} };
  assert.throws(() => new WebSocketTransport({ ...config, url: 'ws://localhost:8080/ws' }));
  assert.throws(() => new WebSocketTransport({ ...config, url: 'wss://name:password@example.com/ws' }));
  assert.throws(() => new WebSocketTransport({ ...config, url: 'wss://example.com/ws?token=x' }));
});
