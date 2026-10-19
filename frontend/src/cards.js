export const ROWS = ['MELEE', 'RANGED', 'SIEGE'];
export const ROW_LABELS = { MELEE: 'Ближний бой', RANGED: 'Дальний бой', SIEGE: 'Осадный ряд' };
export const CATALOG = [
  { id: 'warden', name: 'Страж перевала', row: 'MELEE', power: 4, art: 'tower', flavor: 'Ни шагу за северную границу.' },
  { id: 'rider', name: 'Всадник рассвета', row: 'MELEE', power: 6, art: 'rider', flavor: 'Появляется раньше первого луча.' },
  { id: 'wolf', name: 'Серый клинок', row: 'MELEE', power: 5, art: 'sword', flavor: 'Сталь помнит каждую клятву.' },
  { id: 'archer', name: 'Лесная охотница', row: 'RANGED', power: 5, art: 'archer', flavor: 'Лес всегда на её стороне.' },
  { id: 'scout', name: 'Дозорный туманов', row: 'RANGED', power: 3, art: 'tower', flavor: 'Видит то, что скрывает мгла.' },
  { id: 'crossbow', name: 'Арбалетчик', row: 'RANGED', power: 6, art: 'archer', flavor: 'Один выстрел. Одно решение.' },
  { id: 'catapult', name: 'Камнемёт', row: 'SIEGE', power: 8, art: 'siege', flavor: 'Крепости тоже умеют падать.' },
  { id: 'engineer', name: 'Мастер осады', row: 'SIEGE', power: 4, art: 'siege', flavor: 'Каждая стена — лишь вопрос времени.' },
  { id: 'hero', name: 'Хранитель Севера', row: 'MELEE', power: 10, hero: true, art: 'sword', flavor: 'Его имя переживёт эту зиму.' },
  { id: 'frost', name: 'Лютый мороз', type: 'WEATHER', row: 'MELEE', art: 'weather', flavor: 'Сила обычных бойцов ближнего ряда становится равна 1. Для обеих сторон.' },
  { id: 'fog', name: 'Густой туман', type: 'WEATHER', row: 'RANGED', art: 'weather', flavor: 'Сила обычных бойцов дальнего ряда становится равна 1. Для обеих сторон.' },
  { id: 'rain', name: 'Проливной дождь', type: 'WEATHER', row: 'SIEGE', art: 'weather', flavor: 'Сила обычных осадных бойцов становится равна 1. Для обеих сторон.' },
  { id: 'clear', name: 'Ясное небо', type: 'CLEAR', art: 'sun', flavor: 'Снимает всю погоду с поля.' },
  { id: 'horn', name: 'Боевой рог', type: 'HORN', art: 'horn', flavor: 'Удваивает силу обычных бойцов выбранного своего ряда после погоды.' },
].map(c => ({ type: 'UNIT', ...c }));
export const BY_ID = Object.fromEntries(CATALOG.map(c => [c.id, c]));
// Учебная фиксированная колода: 24 обычных бойца, герой, пять спецкарт.
export const DECK = [...CATALOG.filter(c => c.type === 'UNIT' && !c.hero).flatMap(c => [c.id, c.id, c.id]), 'hero', 'frost', 'fog', 'rain', 'clear', 'horn'];
export const describe = c => c.type === 'UNIT' ? `${c.hero ? 'Герой · ' : ''}${ROW_LABELS[c.row]} · ${c.power} силы` : ({ WEATHER: 'Погода · обе стороны', CLEAR: 'Особая карта', HORN: 'Усиление своего ряда' }[c.type]);
