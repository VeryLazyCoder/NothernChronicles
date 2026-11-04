# Согласованный план реализации Этапа 1: «Чистая предметная модель»

> Данный документ зафиксировал все архитектурные решения, принятые в ходе интервью (`/grill-me`), для реализации **Этапа 1** из [`docs/BACKEND_TODO.md`](../BACKEND_TODO.md).

---

## 1. Зафиксированные архитектурные решения

| Аспект | Принятое решение | Обоснование |
|---|---|---|
| **Базовый пакет** | `ru.practicum.gwint_backend` | Сохраняем сгенерированную структуру `pom.xml`, не требуя лишних рефакторингов |
| **Модель состояния** | Неизменяемые Java `record` + copy-on-write методы | Исключает частичные мутации при сбоях и гарантирует потокобезопасность |
| **Идентификаторы** | Строго типизированные `record`: `PlayerId`, `MatchId`, `CardId`, `InstanceId` | Ошибки перепутанных `cardId` и `instanceId` ловятся на этапе компиляции |
| **Формат `InstanceId`** | Детерминированный: `{playerId}-{cardId}-{seq}` (напр. `player1-warden-1`) | Читаемость в логах и воспроизводимость в тестах по аналогии с `frontend/src/game.js` |
| **Каталог карт** | Доменный интерфейс `CardCatalog` + `JsonCardCatalog` в `infrastructure` | Jackson читает `cards.json` вне `domain`, сохраняя чистоту доменного слоя |
| **Игровое поле** | Выделенный `record Board` с полями `melee`, `ranged`, `siege` и методом `getRow(Row)` | Гарантия наличия всех трёх рядов и типизированный доступ без риска `null`-ключей |
| **Валидация инвариантов** | Двухуровневая: компактные конструкторы records + `MatchInvariants.validate(...)` | Быстрый отказ при локальных ошибках + сквозная проверка 60 карт и каталога |
| **Случайность** | `java.util.random.RandomGenerator` снаружи | Детерминированные тесты по seed, серверный генератор в продакшене |

---

## 2. Структура файлов и классов

```text
backend/
├── src/main/resources/
│   └── cards.json                                     <-- JSON-каталог 14 карт (зеркало cards.js)
├── src/main/java/ru/practicum/gwint_backend/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Row.java                               <-- enum: MELEE, RANGED, SIEGE
│   │   │   ├── CardType.java                          <-- enum: UNIT, WEATHER, CLEAR, HORN
│   │   │   ├── MatchPhase.java                        <-- enum: PLAYING, ROUND_OVER, MATCH_OVER
│   │   │   ├── RoomStatus.java                        <-- enum: WAITING, PLAYING, CLOSED
│   │   │   ├── CardId.java                            <-- record CardId(String value)
│   │   │   ├── InstanceId.java                        <-- record InstanceId(String value)
│   │   │   ├── PlayerId.java                          <-- record PlayerId(String value)
│   │   │   ├── MatchId.java                           <-- record MatchId(String value)
│   │   │   ├── CardDefinition.java                    <-- record шаблона карты
│   │   │   ├── CardInstance.java                      <-- record конкретной копии (instanceId, cardId)
│   │   │   ├── HornPlacement.java                     <-- record (Row row, CardInstance card)
│   │   │   ├── Board.java                             <-- record Board(melee, ranged, siege) + getRow
│   │   │   ├── RoundResult.java                       <-- record RoundResult(round, scores, winnerId)
│   │   │   ├── PlayerState.java                       <-- record состояния игрока с copy-on-write
│   │   │   └── MatchState.java                        <-- record состояния матча с copy-on-write
│   │   ├── rules/
│   │   │   ├── CardCatalog.java                       <-- интерфейс доступа к CardDefinition
│   │   │   ├── DeckFactory.java                       <-- генерация 30 карт, Fisher-Yates, раздача 10/20
│   │   │   └── MatchInvariants.java                   <-- глубокая сквозная проверка состояния
│   │   └── error/
│   │       ├── ErrorCode.java                         <-- enum всех кодов из PROTOCOL.md
│   │       └── RuleViolationException.java            <-- доменное исключение с ErrorCode
│   └── infrastructure/
│       └── catalog/
│           ├── JsonCardCatalog.java                   <-- загрузка cards.json (Jackson) и валидация
│           └── CardCatalogDTO.java                    <-- внутренний DTO десериализации
└── src/test/java/ru/practicum/gwint_backend/
    └── GwintBackendApplicationTests.java              <-- юнит-тесты каталога, колоды и инвариантов
```

---

## 3. Порядок реализации (очередь задач)

### Задача 1. Ресурс `cards.json`
- Создать `backend/src/main/resources/cards.json`.
- Перенести все 14 карт из `frontend/src/cards.js`:
  - 8 обычных юнитов: `warden`, `rider`, `wolf`, `archer`, `scout`, `crossbow`, `catapult`, `engineer`.
  - 1 герой: `hero` (`hero: true`, `power: 10`, `row: MELEE`).
  - 5 спецкарт: `frost` (MELEE), `fog` (RANGED), `rain` (SIEGE), `clear` (null), `horn` (null).

### Задача 2. Доменные перечисления и ошибки
- Создать в `domain/model/`:
  - `Row` (`MELEE`, `RANGED`, `SIEGE`).
  - `CardType` (`UNIT`, `WEATHER`, `CLEAR`, `HORN`).
  - `MatchPhase` (`PLAYING`, `ROUND_OVER`, `MATCH_OVER`).
  - `RoomStatus` (`WAITING`, `PLAYING`, `CLOSED`).
- Создать в `domain/error/`:
  - `ErrorCode` (все 19 кодов из PROTOCOL.md).
  - `RuleViolationException(ErrorCode code, String message)`.

### Задача 3. Идентификаторы, шаблон и экземпляр карты
- Создать value-records: `CardId`, `InstanceId`, `PlayerId`, `MatchId`.
- Создать `CardDefinition` (шаблон) с компактным конструктором, проверяющим неотрицательность силы и валидность рядов.
- Создать `CardInstance(InstanceId instanceId, CardId cardId)`.
- Создать интерфейс `CardCatalog` в `domain/rules/`.

### Задача 4. Инфраструктурный загрузчик каталога (`JsonCardCatalog`)
- Создать `JsonCardCatalog` в `infrastructure/catalog/`:
  - Чтение `cards.json` через ObjectMapper Jackson с использованием `CardCatalogDTO`.
  - Валидация при инициализации: уникальные id, положительная сила у юнитов, корректные ряды.
- Написать юнит-тест десериализации.

### Задача 5. Игровое поле и состояние
- Создать `HornPlacement(Row row, CardInstance card)`.
- Создать `Board(List<CardInstance> melee, List<CardInstance> ranged, List<CardInstance> siege)`:
  - Метод `getRow(Row row)`.
  - Защитное копирование списков `List.copyOf()`.
  - Метод `empty()`.
  - Метод `withAddedCard(CardInstance card, Row row)`.
  - Методы `totalSize()` и `allCards()`.
- Создать `RoundResult(Optional<PlayerId> winner, List<Integer> scores, int round)`.
- Создать `PlayerState`:
  - Поля: `id`, `name`, `lives`, `isPassed`, `hand`, `deck`, `discardedCards`, `board`, `horns`.
  - Компактный конструктор с проверкой `lives` в диапазоне `0..2` и защитным копированием.
  - Методы copy-on-write (`withLives`, `withPassed`, `withHand`, `withDeck`, `withDiscardedCards`, `withBoard`, `withHorns`, `withHornPlacement`).
- Создать `MatchState`:
  - Поля: `id`, `revision`, `phase`, `round`, `starterId`, `activePlayerId`, `players`, `weather`, `ready`, `results`, `winner`, `logs`.
  - Компактный конструктор с проверкой `round` в диапазоне `1..3` и защитой коллекций.
  - Полный набор методов copy-on-write (`withRevision`, `withPhase`, `withRound`, `withActivePlayer`, `withStarter`, `withPlayers`, `withUpdatedPlayer`, `withWeather`, `withReady`, `withResults`, `withWinner`, `withLogsAddRow`).

### Задача 6. Сборка колоды, Fisher–Yates и стартовое состояние
- Создать `DeckFactory`:
  - Сборка 30 карт (24 юнита + 1 герой + 5 спецкарт).
  - Генерация уникальных `InstanceId` вида `{playerId}-{cardId}-{seq}`.
  - Метод `shuffle(List<CardInstance>, RandomGenerator)`.
  - Метод `createInitialPlayerState(PlayerId, String name, RandomGenerator)`.
  - Метод `createInitialMatch(MatchId, PlayerId p1, String name1, PlayerId p2, String name2, RandomGenerator)`.
- Написать тесты на состав колоды, разделение 10/20 и детерминированность тасования Fisher–Yates.

### Задача 7. Сквозная валидация инвариантов и приёмочные тесты
- Создать `MatchInvariants.validate(MatchState state, CardCatalog catalog)`:
  - Проверка, что суммарное число карт у каждого игрока равно 30.
  - Проверка, что в матче ровно 60 уникальных карт (`Collections.disjoint`).
  - Проверка, что у каждого игрока нет дубликатов карт.
  - Проверка, что все `cardId` зарегистрированы в каталоге.
  - Проверка уникальности рогов по рядам у игрока.
  - Проверка корректности `activePlayerId` в зависимости от фазы (`PLAYING` vs `ROUND_OVER`/`MATCH_OVER`).
  - Проверка диапазона жизней (0..2) и раунда (1..3).
  - Проверка, что игроки имеют разные `PlayerId`.
- Покрыть негативными тестами все проверяемые случаи нарушения инвариантов (`checkFailingValidationWithIncorrectRound`).
