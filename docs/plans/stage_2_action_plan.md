# Согласованный план реализации Этапа 2: «Правила и расчёт силы»

> Данный документ фиксирует архитектурные решения, правила предметной области и план реализации **Этапа 2** из [`docs/BACKEND_TODO.md`](../BACKEND_TODO.md) на основе кодовой базы, созданной на первом этапе.

---

## 1. Зафиксированные архитектурные решения

| Аспект | Принятое решение | Обоснование |
|---|---|---|
| **Чистый домен** | Никаких зависимостей от Spring, WebSocket, Jackson или JPA в `domain` | Игровая логика тестируется изолированно и быстро обычным JUnit 5 |
| **Иерархия команд** | `sealed interface DomainCommand` в пакете `domain.command` (`PlayCardCommand`, `PassCommand`, `ReadyNextRoundCommand`) | Полнота обработки в `switch`/pattern matching Java 27, строгая типизация команд |
| **Идентификация актора** | `PlayerId actor` передаётся отдельным аргументом в `GameEngine.apply(state, actor, cmd)` | Команды не содержат доверенных полей пользователя; identity определяет application-слой |
| **Иммутабельность** | Все мутации порождают новый `MatchState` методом copy-on-write | При любой ошибке/отказе старое состояние остаётся нетронутым, `revision` не меняется |
| **Вычисление силы** | Выделенный класс `ScoreCalculator` с зависимостью от `CardCatalog` | Сила не хранится в состоянии как изменяемое поле, а пересчитывается по канонической формуле |
| **Автопас при пустой руке** | При розыгрыше последней карты активный игрок автоматически получает `passed = true` | Соответствует поведению `frontend/src/game.js` и спецификации `BACKEND_TODO.md` |
| **Серии ходов** | Если соперник уже спасовал, активный игрок может совершать несколько ходов подряд | Корректная логика пошагового автомата: ход переключается только на неспасовавшего игрока |
| **Монотонность ревизии** | `state.revision() + 1` строго при успешной мутации команды | Базовое требование протокола для оптимистичной синхронизации с клиентом |

---

## 2. Структура файлов и классов

```text
backend/
├── src/main/java/ru/practicum/gwint_backend/
│   ├── domain/
│   │   ├── command/
│   │   │   ├── DomainCommand.java                     <-- sealed interface (PlayCard, Pass, ReadyNextRound)
│   │   │   ├── PlayCardCommand.java                   <-- record (InstanceId cardInstanceId, Row row)
│   │   │   ├── PassCommand.java                       <-- record PassCommand()
│   │   │   └── ReadyNextRoundCommand.java             <-- record ReadyNextRoundCommand()
│   │   ├── model/
│   │   │   └── PlayerState.java                       <-- добавление методов перемещения карт (рука -> поле/сброс/рог)
│   │   └── rules/
│   │       ├── ScoreCalculator.java                   <-- вычисление силы карты, ряда и стороны игрока
│   │       └── GameEngine.java                        <-- start(...) и apply(MatchState, PlayerId, DomainCommand)
└── src/test/java/ru/practicum/gwint_backend/
    └── domain/
        └── rules/
            ├── ScoreCalculatorTest.java               <-- юнит-тесты формулы силы (герой, погода, рог, ясное небо)
            └── GameEngineTest.java                    <-- юнит-тесты ходов, паса, автопаса, отказов и неизменяемости
```

---

## 3. Порядок реализации (очередь задач)

### Задача 1. Реализация `ScoreCalculator`
Создать `ru.practicum.gwint_backend.domain.rules.ScoreCalculator`:
- **Конструктор:** принимает `CardCatalog` (для поиска `CardDefinition` по `CardId`).
- **Формула силы карты (`calculateCardPower`):**
  - Если `cardDef.isHero() == true` $\rightarrow$ возвращается `cardDef.power()` без изменений (погода и рог игнорируются).
  - Если обычный боец:
    - Погода: если `state.weather().contains(row)` $\rightarrow$ базовая сила `1`, иначе `cardDef.power()`.
    - Рог: если у игрока в ряду `row` выставлен рог (`player.horns().stream().anyMatch(h -> h.row() == row)`) $\rightarrow$ множитель `2`, иначе `1`.
    - Итог: `basePower * hornMultiplier`.
- **Сила ряда (`calculateRowScore`):** сумма сил карт из `player.board().getRow(row)`.
- **Суммарный счёт игрока (`calculatePlayerScore`):** сумма очков по всем трём рядам (`MELEE`, `RANGED`, `SIEGE`).

### Задача 2. Введение доменных команд `DomainCommand`
Создать в пакете `ru.practicum.gwint_backend.domain.command`:
- `DomainCommand` — `public sealed interface DomainCommand permits PlayCardCommand, PassCommand, ReadyNextRoundCommand`.
- `PlayCardCommand(InstanceId cardInstanceId, Row row)`:
  - В компактном конструкторе: проверка `Objects.requireNonNull(cardInstanceId)`.
  - Поле `row` может быть `null` на уровне рекорда (для `WEATHER` и `CLEAR`). Валидация наличия и соответствия `row` типу карты производится позже в `GameEngine`.
- `PassCommand()` — пустой record-сигнал паса.
- `ReadyNextRoundCommand()` — пустой record-сигнал готовности (задел для Этапа 3).

### Задача 3. Доработка методов перемещения карт в `PlayerState`
Раскомментировать и адаптировать вспомогательные методы в `PlayerState.java`:
- `withCardPlayedToBoard(CardInstance card, Row row)`:
  - Удаляет `card` из `hand`.
  - Добавляет `card` в `board.withAddedCard(card, row)`.
- `withHornPlaced(HornPlacement horn)`:
  - Удаляет `horn.card()` из `hand`.
  - Добавляет `horn` в `horns`.
- `withCardDiscarded(CardInstance card)`:
  - Удаляет `card` из `hand`.
  - Добавляет `card` в `discardedCards`.
> [!IMPORTANT]
> Общее количество карт у игрока во всех зонах (`hand + board + horns + discardedCards + deck`) обязано оставаться строго равным 30, чтобы удовлетворять проверкам `MatchInvariants`.

### Задача 4. Реализация `GameEngine`
Создать `ru.practicum.gwint_backend.domain.rules.GameEngine`:
- **Поля:** `CardCatalog catalog`, `ScoreCalculator scoreCalculator`, `DeckFactory deckFactory`.
- **Метод `start(...)`:**
  - Делегирует вызов в `deckFactory.createInitialMatch(...)` (раунд 1, 2 жизни, 10 карт в руке, пустые ряды, случайный starterId).
- **Метод `apply(MatchState state, PlayerId actor, DomainCommand command)`:**
  1. **Предварительная валидация:**
     - Фаза матча: `state.phase() == MatchPhase.PLAYING` (иначе `INVALID_PHASE`).
     - Активный игрок: `state.activePlayerId().equals(actor)` (иначе `NOT_YOUR_TURN`).
     - Игрок в матче: `actor` присутствует в `state.players()` (иначе `FORBIDDEN`).
     - Статус паса: `!player.isPassed()` (иначе `ALREADY_PASSED`).
  2. **Обработка `PlayCardCommand`:**
     - Поиск карты в `player.hand()`. Если не найдена $\rightarrow$ `CARD_NOT_IN_HAND`.
     - Получение `CardDefinition` из каталога.
     - Проверка по типу карты:
       - `UNIT`: `command.row()` не `null` и равен `def.row()` (иначе `INVALID_TARGET`). Карта переходит в `board`.
       - `HORN`: `command.row()` не `null`, и в ряду ещё нет рога (`EFFECT_ALREADY_ACTIVE`). Карта переходит в `horns`.
       - `WEATHER`: ряд берется из `def.row()`. Ряд ещё не активен в `state.weather()` (`EFFECT_ALREADY_ACTIVE`). Карта переходит в `discardedCards`, ряд добавляется в погоду.
       - `CLEAR`: `state.weather()` не пуст (иначе `INVALID_TARGET`). Карта уходит в `discardedCards`, погода очищается.
     - Добавление записи в лог: `"[Имя]: [Название карты]."`.
     - Автопас: если после хода `player.hand().isEmpty()`, выставить `passed = true` и добавить запись в лог `"[Имя]: рука пуста, автоматический пас."`.
  3. **Обработка `PassCommand`:**
     - Выставить `player.withPassed(true)`.
     - Добавить лог: `"[Имя]: пас до конца раунда."`.
  4. **Переключение очереди хода:**
     - Найти второго игрока (`otherPlayer`).
     - Если соперник не спасовал $\rightarrow$ ход переходит ему (`activePlayerId = otherPlayer.id()`).
     - Если соперник уже спасовал, а текущий игрок не спасовал $\rightarrow$ ход остаётся у текущего игрока (`activePlayerId = player.id()`).
     - Если оба спасовали $\rightarrow$ на этапе 2 раунд завершается: фаза переходит в `ROUND_OVER`, `activePlayerId = null` (в соответствии с инвариантом `MatchInvariants`).
  5. **Инкремент ревизии и финальная валидация:**
     - `newRevision = state.revision() + 1`.
     - Защитная валидация `MatchInvariants.validate(newState, catalog)`.

---

## 4. Справочник маппинга ошибок (`ErrorCode`)

| Ситуация | Ожидаемый `ErrorCode` |
|---|---|
| Действие при `phase != PLAYING` | `ErrorCode.INVALID_PHASE` |
| Ход игрока, который не является `activePlayerId` | `ErrorCode.NOT_YOUR_TURN` |
| Попытка хода после паса (`player.isPassed() == true`) | `ErrorCode.ALREADY_PASSED` |
| Экземпляра карты нет в руке активного игрока | `ErrorCode.CARD_NOT_IN_HAND` |
| Неверный ряд у юнита или `row == null` | `ErrorCode.INVALID_TARGET` |
| Рог разыгран без указания ряда (`row == null`) | `ErrorCode.INVALID_TARGET` |
| В выбранном ряду уже установлен боевой рог | `ErrorCode.EFFECT_ALREADY_ACTIVE` |
| Погодный эффект для этого ряда уже действует на столе | `ErrorCode.EFFECT_ALREADY_ACTIVE` |
| Разыграно ясное небо (`CLEAR`), когда на столе нет погоды | `ErrorCode.INVALID_TARGET` |

---

## 5. Тестовые сценарии (JUnit 5)

### Набор 1: `ScoreCalculatorTest`
- **Тест 1 (Эталонная формула):** два бойца силой 4 и 6 + герой силой 10 в ряду `MELEE`:
  - Без эффектов: $4 + 6 + 10 = \mathbf{20}$.
  - Под морозом (`MELEE` в погоде): $1 + 1 + 10 = \mathbf{12}$.
  - Под морозом и рогом: $(1 \times 2) + (1 \times 2) + 10 = \mathbf{14}$.
  - Под рогом при ясном небе: $(4 \times 2) + (6 \times 2) + 10 = \mathbf{30}$.
- **Тест 2 (Изоляция рога):** рог игрока 1 удваивает только его ряд и не меняет силу юнитов игрока 2.
- **Тест 3 (Изоляция погоды):** мороз (`MELEE`) не влияет на ряды `RANGED` и `SIEGE`.
- **Тест 4 (Суммарный счёт игрока):** сложение очков по всем трем рядам даёт корректную общую сумму.

### Набор 2: `GameEngineTest`
- **Тест 1 (Корректные ходы):**
  - Розыгрыш `UNIT` перемещает карту из руки на доску, меняет `activePlayerId`, увеличивает `revision` на 1.
  - Розыгрыш `HORN` размещает карту в `horns`.
  - Розыгрыш `WEATHER` активирует погоду и отправляет карту в `discardedCards`.
  - Розыгрыш `CLEAR` очищает погоду и отправляет карту в `discardedCards`.
- **Тест 2 (Автопас):** игрок с одной картой в руке после её розыгрыша автоматически получает `passed = true`.
- **Тест 3 (Серия ходов):** после паса первого игрока второй может сделать несколько ходов подряд.
- **Тест 4 (Тесты отказов и неизменяемости):**
  - Неверный ход (чужая карта, чужой ход, неверный ряд, дубликат погоды/рога, ясное небо без погоды) выбрасывает `RuleViolationException` с точным `ErrorCode`.
  - **Абсолютная неизменяемость:** после выброса исключения объект `MatchState` остаётся побайтово/структурно неизменным, а ревизия не увеличивается.
- **Тест 5 (Инварианты):** после каждого успешного хода `MatchInvariants.validate` возвращает `true`.

---

## 6. Критерии приёмки (Definition of Done)

1. Все новые классы и методы компилируются без предупреждений на Java 27.
2. Пакет `domain` не содержит внешних зависимостей (Spring, Jackson, JPA).
3. Все тесты в `ScoreCalculatorTest` и `GameEngineTest` выполняются успешно.
4. Полноценная серия ходов воспроизводится в тестах без сетевого слоя (HTTP/WebSocket).
5. Неизменяемость состояния строго соблюдена (чистая функциональная модель).
