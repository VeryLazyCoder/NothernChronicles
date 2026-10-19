// Эталон упрощённых правил для демонстрации. В online-режиме не используется.
import { BY_ID, DECK, ROWS } from './cards.js';
export function createGame(random = Math.random) {
  const player = id => {
    const deck = DECK.map((cardId, i) => ({ instanceId: `${id}-${i}`, cardId }));
    for (let i = deck.length - 1; i > 0; i--) { const j = Math.floor(random() * (i + 1)); [deck[i], deck[j]] = [deck[j], deck[i]]; }
    return { id, name: id === 'you' ? 'Вы' : 'Империя пепла', hand: deck.splice(0, 10), deck, discard: [], board: Object.fromEntries(ROWS.map(r => [r, []])), horns: [], lives: 2, passed: false };
  };
  return { matchId: 'demo', revision: 0, phase: 'PLAYING', round: 1, activePlayerId: 'you', starterId: 'you', players: [player('you'), player('bot')], weather: [], roundResults: [], ready: [], log: ['Матч начался. Первый ход за вами.'], winnerId: null };
}
export function cardPower(state, player, row, card) {
  const def = BY_ID[card.cardId];
  if (def.hero) return def.power;
  return (state.weather.includes(row) ? 1 : def.power) * (player.horns.some(h => h.row === row) ? 2 : 1);
}
export const rowScore = (s, p, r) => p.board[r].reduce((n, c) => n + cardPower(s, p, r, c), 0);
export const score = (s, p) => ROWS.reduce((n, r) => n + rowScore(s, p, r), 0);
function finishRound(s) {
  const [a, b] = s.players, scores = [score(s, a), score(s, b)];
  const winnerId = scores[0] === scores[1] ? null : scores[0] > scores[1] ? a.id : b.id;
  s.roundResults.push({ round: s.round, scores, winnerId });
  for (const p of s.players) if (p.id !== winnerId) p.lives--;
  s.log.push(`Раунд ${s.round}: ${scores[0]} : ${scores[1]}. ${winnerId ? (winnerId === 'you' ? 'Вы победили.' : 'Победил соперник.') : 'Ничья. Оба теряют жизнь.'}`);
  s.phase = s.players.some(p => p.lives === 0) ? 'MATCH_OVER' : 'ROUND_OVER';
  s.activePlayerId = null;
  if (s.phase === 'MATCH_OVER') s.winnerId = a.lives === 0 && b.lives === 0 ? null : a.lives > 0 ? a.id : b.id;
}
export function applyCommand(state, playerId, command) {
  const s = structuredClone(state), p = s.players.find(p => p.id === playerId);
  if (!p) throw Error('Неизвестный игрок.');
  if (command.type === 'READY_NEXT_ROUND') {
    if (s.phase !== 'ROUND_OVER') throw Error('Раунд ещё не завершён.');
    if (s.ready.includes(playerId)) throw Error('Вы уже готовы.');
    s.ready.push(playerId);
    if (s.ready.length === 2) {
      const last = s.roundResults.at(-1);
      s.starterId = last.winnerId ? s.players.find(p => p.id !== last.winnerId).id : s.players.find(p => p.id !== s.starterId).id;
      for (const q of s.players) {
        q.discard.push(...ROWS.flatMap(r => q.board[r]), ...q.horns.map(h => h.card));
        q.board = Object.fromEntries(ROWS.map(r => [r, []])); q.horns = []; q.passed = q.hand.length === 0;
      }
      s.weather = []; s.ready = []; s.round++; s.phase = 'PLAYING'; s.activePlayerId = s.starterId;
      if (s.players.every(q => q.passed)) finishRound(s);
      else if (s.players.find(q => q.id === s.activePlayerId).passed) s.activePlayerId = s.players.find(q => !q.passed).id;
      s.log.push(`Начался раунд ${s.round}. Рука сохраняется, добора нет.`);
    }
  } else {
    if (s.phase !== 'PLAYING' || s.activePlayerId !== playerId || p.passed) throw Error('Сейчас нельзя сделать ход.');
    if (command.type === 'PASS') { p.passed = true; s.log.push(`${p.name}: пас до конца раунда.`); }
    else if (command.type === 'PLAY_CARD') {
      const i = p.hand.findIndex(c => c.instanceId === command.payload?.cardInstanceId);
      if (i < 0) throw Error('Карты нет в вашей руке.');
      const card = p.hand[i], def = BY_ID[card.cardId], row = command.payload.row;
      if (def.type === 'UNIT' && row !== def.row) throw Error('Эта карта предназначена для другого ряда.');
      if (def.type === 'HORN' && (!ROWS.includes(row) || p.horns.some(h => h.row === row))) throw Error('Выберите ряд без боевого рога.');
      if (def.type === 'WEATHER' && s.weather.includes(def.row)) throw Error('Эта погода уже действует.');
      if (def.type === 'CLEAR' && s.weather.length === 0) throw Error('На поле нет погоды.');
      p.hand.splice(i, 1);
      if (def.type === 'UNIT') p.board[row].push(card);
      if (def.type === 'HORN') p.horns.push({ row, card });
      if (def.type === 'WEATHER') { s.weather.push(def.row); p.discard.push(card); }
      if (def.type === 'CLEAR') { s.weather = []; p.discard.push(card); }
      s.log.push(`${p.name}: ${def.name}.`);
      if (!p.hand.length) { p.passed = true; s.log.push(`${p.name}: рука пуста, автоматический пас.`); }
    } else throw Error('Неизвестная команда.');
    if (s.players.every(q => q.passed)) finishRound(s);
    else s.activePlayerId = s.players.find(q => q.id !== playerId && !q.passed)?.id || playerId;
  }
  s.revision++; s.log = s.log.slice(-40); return s;
}
export function botCommand(s) {
  const p = s.players[1], other = s.players[0];
  if (s.phase === 'ROUND_OVER') return { type: 'READY_NEXT_ROUND' };
  const options = p.hand.flatMap(c => {
    const d = BY_ID[c.cardId];
    return (d.type === 'HORN' ? ROWS : [d.row || null]).map(row => ({ type: 'PLAY_CARD', payload: { cardInstanceId: c.instanceId, row } }));
  }).filter(c => { try { applyCommand(s, p.id, c); return true; } catch { return false; } });
  if (!options.length || (other.passed && score(s, p) > score(s, other)) || (!other.passed && p.hand.length < 4 && score(s, other) - score(s, p) > 15)) return { type: 'PASS' };
  options.sort((a, b) => {
    const gain = c => { const next = applyCommand(s, p.id, c); return score(next, next.players[1]) - score(next, next.players[0]); };
    return gain(b) - gain(a);
  });
  return options[0];
}
export function projectView(s, viewerId = 'you') {
  return { ...structuredClone(s), viewerId, players: s.players.map(p => ({
    id: p.id, name: p.name, lives: p.lives, passed: p.passed, board: structuredClone(p.board), horns: structuredClone(p.horns),
    handCount: p.hand.length, deckCount: p.deck.length, discardCount: p.discard.length,
    ...(p.id === viewerId ? { hand: structuredClone(p.hand) } : {})
  })) };
}
