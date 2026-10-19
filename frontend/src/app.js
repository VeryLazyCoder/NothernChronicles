import { CATALOG, BY_ID, DECK, ROWS, ROW_LABELS, describe } from './cards.js';
import { score, rowScore, cardPower } from './game.js';
import { DemoTransport, WebSocketTransport } from './transport.js';

const app = document.querySelector('#app'), modal = document.querySelector('#modal');
const esc = x => String(x ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const glyph = { MELEE: '⚔', RANGED: '➶', SIEGE: '♜', WEATHER: '❄', CLEAR: '☀', HORN: '⚑' };
let transport, view, room, status = 'demo', pending = false, selected = null, tab = 'battle', filter = 'ALL', mode = 'demo';
let toastTimer;
function toast(message) { const el = document.querySelector('#toast'); el.textContent = message; el.classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('visible'), 6000); }
function update(data) {
  view = data.view; room = data.room; status = data.status; pending = data.pending;
  if (selected && !view?.players.find(p => p.id === view.viewerId)?.hand.some(c => c.instanceId === selected)) selected = null;
  render();
}
function demo() { transport?.close(); mode = 'demo'; selected = null; transport = new DemoTransport(update); transport.start(); }
function send(type, payload) { try { transport.send(type, payload); } catch (e) { toast(e.message); } }
function art(kind, cls = '') {
  const shapes = {
    tower: '<path d="M66 178V72h-8V49h14v12h13V43h14v18h13V49h14v23h-8v106Z"/><path d="M78 108h13V88H78Zm0 39h13v-22H78Z" fill="#18292a"/>',
    sword: '<path d="m88 25 9 19-5 81 22 9-4 7-19-5-1 24 5 9-9 10-9-10 6-10 1-24-20 4-3-7 23-8-1-80Z"/>',
    archer: '<path d="M83 76a12 12 0 1 0 0-24 12 12 0 0 0 0 24Zm-9 4-15 49 13 4-8 49h13l9-43 7 43h12l-7-62-5-30 29 12 5-7-34-19Z"/><path d="M127 53q43 45 0 90m0-90v90" fill="none" stroke="currentColor" stroke-width="3"/>',
    rider: '<path d="m45 130 18-37 45-1 17-28 14 15-4 31-13 10-7 50h-9l-2-43-27 4-15 39H51l11-47-14 15Zm32-42 5-30 16 2 9 31Zm3-43a11 11 0 1 0 22 0 11 11 0 1 0-22 0Z"/>',
    siege: '<path d="M39 151h108v10H39Zm24-12 38-68 8 5-38 67Zm42-67 17-31 8 4-18 33ZM76 132l-7-74 8-1 10 82Z"/><circle cx="62" cy="164" r="18"/><circle cx="126" cy="164" r="18"/><path d="m49 143 72-57 6 7-73 56Z"/>',
    weather: '<path d="M93 37v118M43 66l101 60M43 126l101-60M74 48l19 18 19-18M74 144l19-18 19 18M45 89l26-4-4-25m73 28-26-4 4-25m-73 45 26 4-4 25m73-28-26 4 4 25" fill="none" stroke="currentColor" stroke-width="5"/>',
    sun: '<circle cx="93" cy="94" r="29"/><path d="M93 40V23m0 142v-17M39 94H22m142 0h-17M54 55 42 43m102 102-12-12M54 133l-12 12M144 43l-12 12" fill="none" stroke="currentColor" stroke-width="4"/>',
    horn: '<path d="M42 62q15 87 102 43l7 24q-118 48-119-65Z"/><ellipse cx="147" cy="115" rx="9" ry="20" fill="none" stroke="currentColor" stroke-width="4"/>',
  };
  return `<div class="art art-${esc(kind)} ${cls}"><svg viewBox="0 0 186 210" aria-hidden="true"><path class="far-mountain" d="m0 138 34-49 28 25 35-69 43 72 29-29 17 44v78H0Z"/><path class="near-mountain" d="m0 174 50-56 36 33 37-15 63 40v34H0Z"/><g class="figure">${shapes[kind] || shapes.tower}</g><path class="trees" d="m10 190 11-47 11 47h-8v20h-7v-20m127 8 12-57 12 57h-8v12h-8v-12"/></svg></div>`;
}
function cardMarkup(card, { small = false, currentPower = null, collection = false } = {}) {
  const d = BY_ID[card.cardId];
  return `<button class="card ${small ? 'small' : ''} ${d.hero ? 'hero' : ''} ${selected === card.instanceId ? 'selected' : ''}" data-${collection ? 'inspect' : small ? 'field' : 'select'}="${esc(collection ? d.id : card.instanceId)}" aria-label="${esc(d.name + '. ' + describe(d))}" ${selected === card.instanceId ? 'aria-pressed="true"' : ''} title="${esc(d.name + ' — ' + describe(d))}">
    ${art(d.art)}<span class="card-power ${d.hero ? 'hero-power' : ''}">${d.type === 'UNIT' ? (currentPower ?? d.power) : glyph[d.type]}</span>
    <span class="card-foot"><span>${esc(d.name)}</span><i>${d.type === 'UNIT' ? glyph[d.row] : '✦'}</i></span>${d.hero ? '<span class="hero-star">✦</span>' : ''}</button>`;
}
const mine = () => view?.players.find(p => p.id === view.viewerId);
const opponent = () => view?.players.find(p => p.id !== view.viewerId);
const isTurn = () => view?.phase === 'PLAYING' && view.activePlayerId === view.viewerId && !mine()?.passed && !pending && ['demo', 'online'].includes(status);
const selectedCard = () => mine()?.hand.find(c => c.instanceId === selected);
function playerPanel(p, enemy) {
  return `<section class="player-panel ${!enemy ? 'your-panel' : ''}"><div class="eyebrow">${enemy ? 'ПРОТИВНИК' : 'ВАША СТОРОНА'}</div><div class="player-identity"><div class="avatar ${enemy ? 'enemy' : ''}">${enemy ? '♜' : '♔'}</div><div><h2>${esc(p.name)}</h2><span>${enemy ? 'Империя пепла' : 'Северный союз'}</span></div></div><div class="player-stats"><span class="lives" aria-label="Осталось жизней: ${p.lives}">${[0, 1].map(i => `<i class="${i < p.lives ? 'alive' : ''}">◆</i>`).join('')}</span><span class="total-score">${score(view, p)}<small>СИЛА</small></span></div><div class="player-meta"><span>▤ В руке <b>${p.handCount}</b></span><span>Колода <b>${p.deckCount}</b></span></div><div class="player-state ${p.passed ? 'passed' : ''}">${p.passed ? 'Пас до конца раунда' : view.activePlayerId === p.id ? '● Сейчас ходит' : 'Ожидает хода'}</div></section>`;
}
function boardRow(p, r, enemy) {
  const def = selectedCard() && BY_ID[selectedCard().cardId];
  const target = !enemy && isTurn() && def && (def.type === 'UNIT' && def.row === r || def.type === 'HORN' && !p.horns.some(h => h.row === r));
  return `<div class="battle-row ${enemy ? 'enemy-row' : ''} ${view.weather.includes(r) ? 'frozen' : ''} ${target ? 'valid-target' : ''}"><div class="row-label"><span>${glyph[r]}</span><small>${ROW_LABELS[r]}</small><b>${rowScore(view, p, r)}</b></div><div class="row-cards">${p.board[r].map(c => cardMarkup(c, { small: true, currentPower: cardPower(view, p, r, c) })).join('')}${!p.board[r].length ? `<span class="empty-row">${target ? 'Разыграйте карту в этот ряд' : ROW_LABELS[r]}</span>` : ''}</div><div class="row-effect" title="Усиление ряда">${p.horns.some(h => h.row === r) ? '⚑ ×2' : '◇'}</div>${target ? `<button class="row-target" data-row="${r}" aria-label="Разыграть выбранную карту: ${ROW_LABELS[r]}">+</button>` : ''}</div>`;
}
function details() {
  const c = selectedCard(), d = c && BY_ID[c.cardId];
  return `<section class="detail-panel"><div class="eyebrow">${d ? 'ВЫБРАННАЯ КАРТА' : 'ПОЛЕВОЙ СПРАВОЧНИК'}</div>${d ? `${art(d.art, 'detail-art')}<div class="detail-title">${d.hero ? '✦ ' : ''}${esc(d.name)}</div><div class="detail-type">${esc(describe(d))}</div><p>${esc(d.flavor)}</p>${d.hero ? '<p>Не подвержен погоде и действию боевого рога.</p>' : ''}${['WEATHER', 'CLEAR'].includes(d.type) ? `<button class="primary full" data-action="special" ${isTurn() ? '' : 'disabled'}>Разыграть карту</button>` : '<div class="hint">Выберите подсвеченный ряд на своей половине поля.</div>'}<button class="text-button full" data-action="deselect">Отменить выбор</button>` : `<div class="guide-symbol">♧</div><h3>Сила в решениях</h3><p>Иногда лучший ход — сохранить карту для следующего раунда.</p><div class="hint">Выберите карту в руке, затем подсвеченный ряд на поле.</div>`}</section>`;
}
function battle() {
  if (!view) return `<section class="connection-lobby"><div class="eyebrow">ИГРА С ДРУГОМ</div><h1>${room ? 'Знамёна подняты' : 'Ваш стол ждёт'}</h1><p>${room ? 'Отправьте код другу. Матч начнётся, когда подключится второй игрок.' : 'Подключите Java-сервер, затем создайте комнату или присоединитесь по коду.'}</p>${room ? `<div class="room-code">${esc(room.code)}</div><p>${room.players.map(p => esc(p.name)).join(' · ')}</p><button class="secondary" data-action="copy-code">Скопировать код</button>` : ''}<div class="lobby-actions"><button class="primary" data-action="connect">${status === 'online' ? 'Открыть комнаты' : 'Настроить подключение'}</button><button class="secondary" data-action="demo">Играть с ботом</button></div><small>Состояние: ${esc(status)}</small></section>`;
  const me = mine(), them = opponent();
  const result = view.phase === 'MATCH_OVER' ? (view.winnerId === null ? 'Матч завершён вничью' : view.winnerId === me.id ? 'Победа за вами' : 'Победа соперника') : 'Раунд завершён';
  return `<div class="match-heading"><div><div class="eyebrow">${mode === 'demo' ? 'ТРЕНИРОВОЧНЫЙ МАТЧ' : 'СЕТЕВОЙ МАТЧ'}</div><h1>Битва за Север</h1></div><div class="round-label">РАУНД <strong>${view.round}</strong><span>/ 3</span></div><button class="icon-button" data-action="help" aria-label="Правила игры">?</button></div>
  <div class="game-layout"><aside class="players">${playerPanel(them, true)}<div class="versus"><span></span> VS <span></span></div>${playerPanel(me, false)}<div class="deck-note"><img src="./assets/crest.svg" alt=""><span>СЕВЕРНЫЙ СОЮЗ<small>Стойкость. Честь. Победа.</small></span></div></aside>
  <section class="table" aria-label="Игровое поле">${[...ROWS].reverse().map(r => boardRow(them, r, true)).join('')}
  <div class="weather-line"><span>✦</span><div>${view.weather.length ? view.weather.map(r => `<span class="weather-chip">${glyph[r]} ${esc(ROW_LABELS[r])}</span>`).join('') : 'Ясное небо · погодных эффектов нет'}</div><span>✦</span></div>
  ${ROWS.map(r => boardRow(me, r, false)).join('')}
  <div class="turn-bar"><div><span class="turn-dot ${isTurn() ? 'active' : ''}"></span><strong>${pending ? 'Ожидаем ответ сервера' : view.phase !== 'PLAYING' ? 'Раунд завершён' : me.passed ? 'Вы спасовали' : isTurn() ? 'Ваш ход' : 'Ход соперника'}</strong><small>${selected ? 'Выберите ряд для карты' : 'Каждая карта имеет значение'}</small></div><button class="primary pass-button" data-action="pass" ${isTurn() ? '' : 'disabled'}>Пас <span>→</span></button></div>
  ${view.phase !== 'PLAYING' ? `<div class="result-banner" role="status"><div class="eyebrow">${view.roundResults.at(-1)?.scores.join(' : ')}</div><h2>${result}</h2><p>${view.phase === 'ROUND_OVER' ? 'Карты в руке сохраняются. Новые карты не добираются.' : 'Судьбу битвы решают те, кто умеет ждать.'}</p>${view.phase === 'ROUND_OVER' ? `<button class="primary" data-action="next" ${pending || view.ready.includes(me.id) || !['online', 'demo'].includes(status) ? 'disabled' : ''}>${view.ready.includes(me.id) ? 'Ожидаем соперника…' : 'Следующий раунд'}</button>` : mode === 'demo' ? '<button class="primary" data-action="restart">Новая партия</button>' : '<button class="secondary" data-action="connect">Открыть подключение</button>'}</div>` : ''}</section>
  <aside class="right-sidebar">${details()}<section class="chronicle"><div class="eyebrow">ХРОНИКА МАТЧА <span>◷</span></div><ol>${view.log.slice(-5).reverse().map((l, i) => `<li class="${i === 0 ? 'latest' : ''}">${esc(l)}</li>`).join('')}</ol></section></aside></div>
  <section class="hand-section" aria-label="Ваша рука"><div class="hand-heading"><div><span class="eyebrow">ВАША РУКА</span><span class="hand-count">${me.handCount} карт</span></div><span>Выберите карту, чтобы сделать ход</span><button class="text-button" data-action="restart" ${mode === 'online' ? 'disabled' : ''}>↻ Новая партия</button></div>${selectedCard() ? `<div class="selection-toolbar"><span><b>${esc(BY_ID[selectedCard().cardId].name)}</b> · ${esc(describe(BY_ID[selectedCard().cardId]))}</span>${['WEATHER', 'CLEAR'].includes(BY_ID[selectedCard().cardId].type) ? `<button class="primary" data-action="special" ${isTurn() ? '' : 'disabled'}>Разыграть</button>` : '<small>Выберите подсвеченный ряд</small>'}<button class="text-button" data-action="deselect">Отменить</button></div>` : ''}<div class="hand">${me.hand.map(c => cardMarkup(c)).join('') || '<p class="muted">Карты закончились. Вы автоматически спасовали.</p>'}</div></section>`;
}
function collection() {
  return `<div class="page-heading"><div class="eyebrow">АРСЕНАЛ СЕВЕРНОГО СОЮЗА</div><h1>Каждая карта — история</h1><p>Фиксированная учебная колода: ${DECK.length} карт, 25 бойцов и 5 особых карт. Редактор колоды — следующий этап проекта.</p></div><div class="filters" role="group" aria-label="Фильтр карт">${[['ALL', 'Все карты'], ['MELEE', 'Ближний бой'], ['RANGED', 'Дальний бой'], ['SIEGE', 'Осада'], ['SPECIAL', 'Особые']].map(([id, label]) => `<button class="${filter === id ? 'active' : ''}" data-filter="${id}">${label}</button>`).join('')}</div><div class="collection-grid">${CATALOG.filter(d => filter === 'ALL' || filter === 'SPECIAL' && d.type !== 'UNIT' || d.type === 'UNIT' && d.row === filter).map(d => `<div>${cardMarkup({ cardId: d.id, instanceId: d.id }, { collection: true })}<h3>${esc(d.name)}</h3><p>${esc(describe(d))}</p><small>В колоде: ${DECK.filter(id => id === d.id).length}</small></div>`).join('')}</div>`;
}
function rules() {
  return `<div class="page-heading"><div class="eyebrow">ПЕРЕД ПЕРВОЙ БИТВОЙ</div><h1>Искусство побеждать</h1><p>Вдохновлено классической механикой Гвинта из «Ведьмака 3». Это самостоятельный учебный прототип с упрощёнными правилами и собственными картами.</p></div><div class="rules-grid">${[
    ['01', 'Выиграйте два раунда', 'У каждой стороны две жизни. Проигравший раунд теряет одну; при ничьей жизнь теряют оба. Если обе стороны одновременно теряют последнюю жизнь, матч завершается вничью.'],
    ['02', 'Распоряжайтесь рукой', 'В начале матча вы получаете 10 карт. Играйте по одной карте за ход. Между раундами добора нет: оставшиеся карты — ваш главный ресурс. Пустая рука означает автоматический пас.'],
    ['03', 'Займите свой ряд', 'Бойцы делятся на ближний, дальний и осадный ряды. Каждую карту можно положить только в указанный ряд своей стороны. Сила всех трёх рядов складывается.'],
    ['04', 'Пас — это решение', 'Пас действует до конца раунда. Соперник может продолжать играть, пока тоже не спасует. После двух пасов сравнивается сила. Следующий раунд начинает проигравший; при ничьей первый игрок меняется.'],
    ['05', 'Погода общая', 'Мороз, туман и дождь снижают базовую силу обычных бойцов соответствующего ряда до 1 у обеих сторон. Ясное небо снимает всю погоду. Повторить активную погоду или снять отсутствующую нельзя.'],
    ['06', 'Усиливайте с умом', 'Боевой рог удваивает силу обычных бойцов одного своего ряда после применения погоды. Один рог на ряд. Герои сохраняют свою силу: погода и рог на них не действуют.'],
  ].map(([n, title, body]) => `<article><span>${n}</span><h2>${title}</h2><p>${body}</p></article>`).join('')}</div><div class="rules-note">В этой версии нет лидеров, шпионов, медиков, чучел, сброса начальной руки и фракционных способностей. Имена фракций пока влияют только на оформление.</div>`;
}
function render() {
  const labels = { demo: 'Локальный бот', online: 'Сервер подключён', connecting: 'Подключение…', offline: 'Нет соединения', syncing: 'Восстановление…', incompatible: 'Ошибка протокола' };
  app.innerHTML = `<div class="app-shell"><header class="topbar"><a class="brand" href="#" data-action="home"><img src="./assets/crest.svg" alt=""><span>СЕВЕРНЫЕ ХРОНИКИ<small>КАРТОЧНАЯ СТРАТЕГИЯ</small></span></a><nav aria-label="Основная навигация">${[['battle', 'Игровой стол'], ['collection', 'Коллекция'], ['rules', 'Как играть']].map(([id, title]) => `<button data-tab="${id}" class="${tab === id ? 'active' : ''}" ${tab === id ? 'aria-current="page"' : ''}>${title}</button>`).join('')}</nav><button class="connection-pill ${status === 'demo' || status === 'online' ? 'connected' : ''}" data-action="connect"><i></i>${labels[status] || esc(status)}<span>⌄</span></button></header><main>${tab === 'battle' ? battle() : tab === 'collection' ? collection() : rules()}</main><footer><span>✦ СЕВЕР ПОМНИТ</span><span>Учебный проект · ${mode === 'demo' ? 'Демонстрация без сервера' : 'WebSocket · JSON v1'}</span><button data-tab="rules">Правила игры ↗</button></footer></div>`;
}
function openDialog(content) { modal.innerHTML = `<button class="modal-close" data-close aria-label="Закрыть">×</button>${content}`; if (!modal.open) modal.showModal(); }
function connectionDialog() {
  openDialog(`<div class="eyebrow">ИГРА ПО СЕТИ</div><h2>Поднять знамёна</h2><p>Подключите собственный Java-сервер. Формат сообщений описан в docs/PROTOCOL.md.</p>${mode === 'online' && status === 'online' ? `<div class="status-box">Соединение установлено</div>${!view && !room ? '<button class="primary full" data-action="create-room">Создать комнату</button><form id="join-form"><label>Код комнаты<input name="code" required minlength="4" maxlength="12" pattern="[A-Za-z0-9]{4,12}" placeholder="Например, NORTH7" autocomplete="off"></label><button class="secondary full" type="submit">Присоединиться</button></form>' : '<p>Вы уже в комнате или матче. Возврат к боту закрывает соединение; на сервере должна работать отсрочка отключения.</p>'}` : `<form id="connect-form"><label>Ваше имя<input name="nickname" required minlength="2" maxlength="24" value="${esc(localStorage.getItem('nc.nickname') || 'Путник')}" autocomplete="nickname"></label><label>Адрес WebSocket<input name="url" type="url" required value="${esc(localStorage.getItem('nc.url') || 'ws://localhost:8080/ws')}" placeholder="ws://localhost:8080/ws"></label><button type="submit" class="primary full">Подключить сервер</button></form>`}<button class="text-button full" data-action="demo">Вернуться к игре с ботом</button><small class="muted">Подключение создаёт гостевую HTTP-сессию, затем открывает WebSocket. Без написанного бэкенда доступна игра с ботом.</small>`);
}
document.addEventListener('click', e => {
  const b = e.target.closest('button, a[data-action]'); if (!b || b.disabled) return;
  if (b.hasAttribute('data-close')) { modal.close(); return; }
  if (b.dataset.tab) { tab = b.dataset.tab; render(); return; }
  if (b.dataset.filter) { filter = b.dataset.filter; render(); return; }
  if (b.dataset.select) { selected = selected === b.dataset.select ? null : b.dataset.select; render(); return; }
  if (b.dataset.field) {
    const c = view.players.flatMap(p => ROWS.flatMap(r => p.board[r])).find(c => c.instanceId === b.dataset.field);
    if (c) inspect(c.cardId); return;
  }
  if (b.dataset.inspect) { inspect(b.dataset.inspect); return; }
  if (b.dataset.row && selected && isTurn()) { send('PLAY_CARD', { cardInstanceId: selected, row: b.dataset.row }); return; }
  switch (b.dataset.action) {
    case 'home': e.preventDefault(); tab = 'battle'; render(); break;
    case 'help': tab = 'rules'; render(); break;
    case 'connect': connectionDialog(); break;
    case 'demo': modal.close(); tab = 'battle'; demo(); break;
    case 'restart':
      if (view?.phase === 'MATCH_OVER') demo();
      else openDialog('<h2>Начать новую партию?</h2><p>Текущий тренировочный матч будет сброшен.</p><button class="primary full" data-action="demo">Начать заново</button><button class="text-button full" data-close>Продолжить матч</button>'); break;
    case 'deselect': selected = null; render(); break;
    case 'special': { const d = BY_ID[selectedCard()?.cardId]; if (d && isTurn()) send('PLAY_CARD', { cardInstanceId: selected, row: null }); break; }
    case 'pass': openDialog('<h2>Спасовать в этом раунде?</h2><p>Вы больше не сможете играть карты до следующего раунда. Соперник сможет продолжать ходить.</p><button class="primary full" data-action="confirm-pass">Подтвердить пас</button><button class="text-button full" data-close>Вернуться к игре</button>'); break;
    case 'confirm-pass': modal.close(); send('PASS'); break;
    case 'next': send('READY_NEXT_ROUND'); break;
    case 'create-room': modal.close(); send('CREATE_ROOM'); break;
    case 'copy-code': navigator.clipboard?.writeText(room.code).then(() => toast('Код комнаты скопирован.')).catch(() => toast('Код комнаты: ' + room.code)); break;
  }
});
function inspect(cardId) { const d = BY_ID[cardId]; openDialog(`<div class="inspect">${art(d.art, 'detail-art')}<div class="eyebrow">${esc(describe(d))}</div><h2>${esc(d.name)}</h2><p>${esc(d.flavor)}</p>${d.hero ? '<p>Герой: погода и боевой рог не изменяют его силу.</p>' : ''}</div>`); }
document.addEventListener('submit', e => {
  if (e.target.id === 'connect-form') {
    e.preventDefault(); const data = new FormData(e.target), nickname = data.get('nickname').trim(), url = data.get('url').trim();
    if (nickname.length < 2) { toast('Имя должно содержать хотя бы два символа.'); return; }
    try {
      const next = new WebSocketTransport({ url, nickname, onUpdate: update, onError: toast });
      transport?.close(); transport = next; mode = 'online'; view = null; selected = null; room = null;
      localStorage.setItem('nc.url', url); localStorage.setItem('nc.nickname', nickname);
      modal.close(); tab = 'battle'; transport.start();
    } catch (e) { toast(e.message); }
  }
  if (e.target.id === 'join-form') { e.preventDefault(); const code = new FormData(e.target).get('code').trim().toUpperCase(); modal.close(); send('JOIN_ROOM', { code }); }
});
modal.addEventListener('click', e => { if (e.target === modal) { const r = modal.getBoundingClientRect(); if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) modal.close(); } });
window.addEventListener('beforeunload', () => transport?.close());
demo();
