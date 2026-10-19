import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGame, applyCommand, rowScore, score, botCommand, projectView } from '../frontend/src/game.js';
import { validateView } from '../frontend/src/transport.js';
import { DECK } from '../frontend/src/cards.js';

const card = (cardId, id = cardId) => ({ cardId, instanceId: id });
const play = (s, who, cardInstanceId, row = null) => applyCommand(s, who, { type: 'PLAY_CARD', payload: { cardInstanceId, row } });
const pass = (s, who) => applyCommand(s, who, { type: 'PASS' });
function fixture() { const s = createGame(() => .5); s.players.forEach(p => { p.hand = [card('warden', p.id + 'w'), card('hero', p.id + 'h')]; }); return s; }

test('30 карт на игрока, по 10 в руке, уникальные instanceId', () => {
  const s = createGame(); assert.equal(DECK.length, 30);
  for (const p of s.players) { assert.equal(p.hand.length, 10); assert.equal(p.deck.length, 20); }
  assert.equal(new Set(s.players.flatMap(p => [...p.deck, ...p.hand]).map(c => c.instanceId)).size, 60);
});
test('проекция скрывает руку и порядок колоды соперника', () => {
  const s = createGame(), v = projectView(s); validateView(v);
  assert.equal(v.players[0].hand.length, 10); assert.equal(v.players[1].hand, undefined);
  assert.ok(v.players.every(p => !('deck' in p))); v.players[0].hand.pop(); assert.equal(s.players[0].hand.length, 10);
});
test('нелегальный ход не меняет исходное состояние', () => {
  const s = fixture(), before = structuredClone(s);
  assert.throws(() => play(s, 'you', 'youw', 'SIEGE')); assert.deepEqual(s, before);
  assert.throws(() => play(s, 'bot', 'botw', 'MELEE')); assert.throws(() => play(s, 'you', 'botw', 'MELEE'));
  assert.throws(() => applyCommand(s, 'you', { type: 'DELETE_ALL' }));
});
test('одна карта занимает ход; пустая рука автоматически пасует', () => {
  let s = fixture(); s.players[0].hand = [card('warden')]; s = play(s, 'you', 'warden', 'MELEE');
  assert.equal(s.players[0].passed, true); assert.equal(s.activePlayerId, 'bot'); assert.equal(s.revision, 1);
});
test('погода общая, рог после погоды, герой не меняет силу', () => {
  const s = fixture(); s.players.forEach(p => { p.board.MELEE = [card('warden'), card('hero')]; });
  assert.equal(score(s, s.players[0]), 14); s.weather = ['MELEE'];
  assert.equal(rowScore(s, s.players[0], 'MELEE'), 11); assert.equal(rowScore(s, s.players[1], 'MELEE'), 11);
  s.players[0].horns.push({ row: 'MELEE', card: card('horn') }); assert.equal(score(s, s.players[0]), 12);
  s.weather = []; assert.equal(score(s, s.players[0]), 18);
});
test('нельзя повторять погоду/рог и разыгрывать ясное небо без погоды', () => {
  const s = fixture(); s.players[0].hand = [card('clear'), card('horn'), card('frost')];
  assert.throws(() => play(s, 'you', 'clear')); s.weather = ['MELEE']; assert.throws(() => play(s, 'you', 'frost'));
  s.players[0].horns = [{ row: 'MELEE', card: card('horn', 'old') }]; assert.throws(() => play(s, 'you', 'horn', 'MELEE'));
  assert.equal(play(s, 'you', 'clear').weather.length, 0);
});
test('после паса соперник может играть несколько карт подряд', () => {
  let s = pass(fixture(), 'you'); s = play(s, 'bot', 'botw', 'MELEE');
  assert.equal(s.activePlayerId, 'bot'); assert.throws(() => play(s, 'you', 'youw', 'MELEE'));
  s = play(s, 'bot', 'both', 'MELEE'); assert.equal(s.phase, 'ROUND_OVER'); assert.equal(s.players[0].lives, 1);
});
test('ничья снимает обе жизни, две ничьи завершают матч', () => {
  let s = pass(pass(fixture(), 'you'), 'bot'); assert.deepEqual(s.players.map(p => p.lives), [1, 1]);
  s = applyCommand(s, 'you', { type: 'READY_NEXT_ROUND' }); assert.equal(s.phase, 'ROUND_OVER');
  assert.throws(() => applyCommand(s, 'you', { type: 'READY_NEXT_ROUND' }));
  s = applyCommand(s, 'bot', { type: 'READY_NEXT_ROUND' }); assert.equal(s.activePlayerId, 'bot');
  s = pass(pass(s, 'bot'), 'you'); assert.equal(s.phase, 'MATCH_OVER'); assert.equal(s.winnerId, null);
});
test('следующий раунд: рука сохраняется, поле и эффекты очищаются, начинает проигравший', () => {
  let s = fixture(); s = play(s, 'you', 'youw', 'MELEE'); s = pass(s, 'bot'); s = pass(s, 'you');
  const counts = s.players.map(p => p.hand.length); s.weather = ['MELEE'];
  s = applyCommand(s, 'you', { type: 'READY_NEXT_ROUND' }); s = applyCommand(s, 'bot', { type: 'READY_NEXT_ROUND' });
  assert.equal(s.round, 2); assert.equal(s.activePlayerId, 'bot'); assert.deepEqual(s.players.map(p => p.hand.length), counts);
  assert.equal(s.players[0].discard.length, 1); assert.equal(s.weather.length, 0); assert.equal(score(s, s.players[0]), 0);
});
test('две победы завершают матч; команды после окончания отклоняются', () => {
  let s = fixture(); s = play(s, 'you', 'youw', 'MELEE'); s = pass(pass(s, 'bot'), 'you');
  s = applyCommand(s, 'you', { type: 'READY_NEXT_ROUND' }); s = applyCommand(s, 'bot', { type: 'READY_NEXT_ROUND' });
  s = pass(s, 'bot'); s = play(s, 'you', 'youh', 'MELEE');
  assert.equal(s.phase, 'MATCH_OVER'); assert.equal(s.winnerId, 'you'); assert.throws(() => pass(s, 'you'));
});
test('сетевая граница отклоняет неизвестные карты и утечку чужой руки', () => {
  const v = projectView(createGame()); v.players[1].hand = []; assert.throws(() => validateView(v));
  delete v.players[1].hand; v.players[0].hand[0].cardId = 'not-in-catalog'; assert.throws(() => validateView(v));
});
test('100 полных партий бота: нет зависания, отрицательных жизней и потери карт', () => {
  for (let i = 0; i < 100; i++) {
    let s = createGame(), steps = 0;
    while (s.phase !== 'MATCH_OVER') {
      assert.ok(++steps < 100);
      if (s.phase === 'ROUND_OVER') {
        s = applyCommand(s, 'you', { type: 'READY_NEXT_ROUND' }); s = applyCommand(s, 'bot', { type: 'READY_NEXT_ROUND' });
      } else {
        const who = s.activePlayerId;
        const asBot = who === 'bot' ? s : { ...s, players: [s.players[1], s.players[0]] };
        s = applyCommand(s, who, botCommand(asBot));
      }
      validateView(projectView(s));
      for (const p of s.players) {
        assert.ok(p.lives >= 0 && p.lives <= 2);
        const cards = [...p.hand, ...p.deck, ...p.discard, ...Object.values(p.board).flat(), ...p.horns.map(h => h.card)];
        assert.equal(cards.length, 30); assert.equal(new Set(cards.map(c => c.instanceId)).size, 30);
      }
    }
    assert.ok(s.round <= 3);
  }
});
