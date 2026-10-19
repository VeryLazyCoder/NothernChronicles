# JSON-протокол v1 — контракт готового frontend

Этот файл описывает требуемый сервер, а не уже работающую службу. Клиент использует **native WebSocket, текстовые JSON-фреймы**, без STOMP, SockJS, брокера и `/topic`. Реализуйте точные имена полей. HTTP-сервер раздачи статики из `tools/` этих API не содержит.

## HTTP-сессия

`POST http://localhost:8080/api/session`, credentials include, Content-Type application/json:

```json
{"nickname":"Путник"}
```

Имя 2–24 символа после trim, сервер дополнительно проверяет длину и допустимые управляющие символы. Успех: 200 (существующая сессия) или 201 (новая), тело может быть `{"nickname":"Путник"}` — текущий клиент его не читает. Ответ устанавливает HttpOnly session cookie. Не пересоздавайте playerId на каждый POST при действующей сессии: это сломает RESUME. Во время матча имя существующего игрока сохраняйте.

Ошибки: 400 невалидное имя, 429 лимит сессий. REST должен обрабатывать OPTIONS preflight. Для разных локальных портов разрешите точный origin `http://localhost:5173` и credentials; `Access-Control-Allow-Origin: *` здесь не подходит. WebSocket-handshake также проверяет Origin, но настройка REST CORS сама по себе его не заменяет. При тестировании используйте везде `localhost`, не смешивайте его с `127.0.0.1`.

Затем клиент открывает `ws://localhost:8080/ws`. Identity берётся из HTTP cookie в handshake, а не из nickname или JSON-команды. Для HTTPS frontend используйте `wss://` и HTTPS session endpoint. В этом MVP хост HTTP API вычисляется из WebSocket URL, путь session endpoint фиксирован `/api/session`; пользовательский context-path сервера не предусмотрен.

## Общая оболочка команды

```json
{
  "protocolVersion": 1,
  "requestId": "b52705ba-5058-4bd4-b0f7-f4d88df44b97",
  "type": "PLAY_CARD",
  "matchId": "match-uuid",
  "expectedRevision": 6,
  "payload": {"cardInstanceId":"a-warden-2","row":"MELEE"}
}
```

requestId — UUID новой попытки команды, уникален на стороне игрока. Сервер ограничивает его длину, проверяет формат и кеширует результат. Для действий комнаты и первого RESUME matchId/expectedRevision могут быть null. Для игровых команд оба обязательны. Клиент не отправляет timestamp, playerId и вычисленную силу. Размер сообщения на сервере ограничьте, например, 16 КиБ; неизвестные типы и неверные enum возвращают ERROR.

| type | payload | Условия и успешный ответ |
|---|---|---|
| CREATE_ROOM | `{}` | Игрок ещё не в активной комнате/матче; ROOM_STATE |
| JOIN_ROOM | `{"code":"NORTH7"}` | Код 4–12 латинских букв/цифр, frontend переводит в верхний регистр; ROOM_STATE либо сразу SNAPSHOT при старте |
| PLAY_CARD | `{"cardInstanceId":"…","row":"MELEE"}` | UNIT: собственный разрешённый ряд; HORN: выбранный свой ряд; WEATHER/CLEAR: row=null; SNAPSHOT |
| PASS | `{}` | Текущий ход и PLAYING; SNAPSHOT |
| READY_NEXT_ROUND | `{}` | ROUND_OVER, игрок ещё не готов; SNAPSHOT |
| RESUME | `{"matchId":"…","roomId":null}` | Принадлежность identity; SNAPSHOT или ROOM_STATE, без мутации |

Поля matchId верхнего уровня и payload.matchId в RESUME должны совпадать, если оба заполнены. roomId известен клиенту до начала матча; после SNAPSHOT клиент хранит только matchId. RESUME с одной комнатой может вернуть SNAPSHOT, если второй участник уже запустил матч.

В MVP второй участник автоматически запускает матч, отдельной кнопки ready в лобби нет. Сервер атомарно создаёт MatchState и отправляет по персональному SNAPSHOT двум игрокам. Второму игроку не требуется промежуточный ROOM_STATE; если вы его отправляете, ответ с тем же requestId должен быстро завершаться последующим SNAPSHOT.

## Общая оболочка ответа

```json
{
  "protocolVersion": 1,
  "requestId": "b52705ba-5058-4bd4-b0f7-f4d88df44b97",
  "type": "SNAPSHOT",
  "payload": {}
}
```

У инициатора копируйте requestId команды; у второго игрока requestId=null. `SNAPSHOT` — полное актуальное состояние для конкретного получателя. `ROOM_STATE` — полное лобби. `ERROR` — отказ без мутации. **Отдельного ACK нет**: эти ответы подтверждают обработку. Не присылайте неподдерживаемые PONG/EVENT/ROUND_ENDED; для heartbeat используйте транспортные WebSocket ping/pong, которые обрабатывает браузер. Хроника находится в snapshot.log.

После SNAPSHOT/ROOM_STATE/ERROR с совпавшим requestId клиент снимает pending. Нельзя после команды отвечать только уведомлением второму игроку, оставляя инициатора без ответа. При игре по очереди revision растёт строго на 1 при каждой принятой команде. Снимки с меньшей revision одного matchId клиент игнорирует. Повторный RESUME и дубликат requestId могут вернуть ту же или более новую revision.

### ROOM_STATE

```json
{
  "protocolVersion":1,
  "requestId":"request-uuid",
  "type":"ROOM_STATE",
  "payload":{
    "roomId":"room-uuid",
    "code":"NORTH7",
    "status":"WAITING",
    "players":[{"id":"player-a","name":"Путник"}]
  }
}
```

В комнате максимум два участника. Код создаётся сервером, уникален среди активных комнат. Гость не может войти сам к себе второй стороной. Запрещён перебор: ограничьте частоту JOIN_ROOM и не выдавайте информацию о чужих матчах. У гостя не более одной активной комнаты или игры.

### SNAPSHOT

Пример иллюстрирует середину короткого матча. В настоящем старте своя рука содержит 10 экземпляров, handCount совпадает с её длиной. Это не шаблон для хардкода Java-состояния.

```json
{
  "protocolVersion":1,
  "requestId":"request-uuid",
  "type":"SNAPSHOT",
  "payload":{
    "matchId":"match-uuid",
    "revision":6,
    "viewerId":"player-a",
    "phase":"PLAYING",
    "round":1,
    "starterId":"player-a",
    "activePlayerId":"player-a",
    "winnerId":null,
    "weather":["RANGED"],
    "ready":[],
    "roundResults":[],
    "log":["Матч начался.","Соперник: Густой туман."],
    "players":[
      {
        "id":"player-a",
        "name":"Путник",
        "lives":2,
        "passed":false,
        "handCount":2,
        "deckCount":20,
        "discardCount":0,
        "hand":[
          {"instanceId":"a-warden-2","cardId":"warden"},
          {"instanceId":"a-clear-1","cardId":"clear"}
        ],
        "board":{"MELEE":[],"RANGED":[],"SIEGE":[]},
        "horns":[]
      },
      {
        "id":"player-b",
        "name":"Странник",
        "lives":2,
        "passed":false,
        "handCount":3,
        "deckCount":20,
        "discardCount":1,
        "board":{
          "MELEE":[{"instanceId":"b-wolf-1","cardId":"wolf"}],
          "RANGED":[],
          "SIEGE":[]
        },
        "horns":[{"row":"MELEE","card":{"instanceId":"b-horn-1","cardId":"horn"}}]
      }
    ]
  }
}
```

`players` — постоянный порядок мест от начала до конца матча, независимо от viewerId. Счёт в RoundResult использует тот же порядок. Клиент сам располагает viewer снизу. Имена выводятся как текст с экранированием. `ready` — массив playerId подтвердивших следующий раунд. В PLAYING activePlayerId обязателен, в ROUND_OVER/MATCH_OVER он null. В MATCH_OVER winnerId — победитель или null при ничьей. round: 1..3, lives: 0..2, revision и счётчики — неотрицательные целые.

RoundResult:

```json
{"round":1,"scores":[24,17],"winnerId":"player-a"}
```

board всегда содержит три массива. horns содержит `{row, card}`; не просто список названий рядов. weather — только список рядов, по одному эффекту каждого вида. `hand` обязательно присутствует только у viewer, у соперника поле отсутствует полностью. У обеих сторон запрещено поле deck; только deckCount. Не передавайте карты сброса — текущему UI достаточно discardCount. log — до 40 безопасных строк, без секретных данных. Каталог открытых карт встроен во frontend; список в `cards.js` — фиксированный контракт MVP.

### ERROR

```json
{
  "protocolVersion":1,
  "requestId":"request-uuid",
  "type":"ERROR",
  "payload":{"code":"NOT_YOUR_TURN","message":"Сейчас ход соперника."}
}
```

Не возвращайте stack trace, имена Java-классов, SQL, cookie и приватные данные. Если JSON повреждён и requestId извлечь нельзя, requestId=null. Для неавторизованного handshake предпочтителен отказ до открытия сокета. Не закрывайте здоровое соединение из-за обычной ошибки правил.

| code | Смысл |
|---|---|
| INVALID_MESSAGE | JSON/тип/обязательные поля/размер не соответствуют контракту |
| UNSUPPORTED_PROTOCOL | Не protocolVersion=1 |
| UNAUTHENTICATED | Сессия истекла или отсутствует |
| FORBIDDEN | Не участник комнаты/матча |
| ROOM_NOT_FOUND / ROOM_FULL | Вход невозможен |
| ALREADY_IN_ROOM | Игрок уже занят |
| MATCH_NOT_FOUND | Игра истекла или сервер перезапущен |
| STALE_REVISION | Команда основана на устаревшем состоянии |
| REQUEST_ID_REUSE | requestId повторён с другим содержимым |
| NOT_YOUR_TURN / INVALID_PHASE / ALREADY_PASSED | Неверный момент действия |
| CARD_NOT_IN_HAND / INVALID_TARGET / EFFECT_ALREADY_ACTIVE | Нарушение правил карты |
| ALREADY_READY | Повторная готовность новым requestId |
| RATE_LIMITED | Слишком частые запросы |
| INTERNAL_ERROR | Непредвиденная ошибка с безопасным текстом |

При STALE_REVISION сервер после ERROR немедленно присылает актуальный SNAPSHOT тому же игроку; requestId сохраните. Клиент не переотправляет ход автоматически. При повторе исходного requestId с тем же содержимым сервер возвращает текущий снимок с этим requestId без повторного применения действия. Ошибки не увеличивают revision.

## Переподключение и ограничения клиента

После timeout ответа (8 секунд) клиент считает исход хода неизвестным, закрывает сокет и восстанавливается через RESUME. На разрыве не посылается PASS или LEAVE. Сервер сохраняет матч и identity на время grace period. Ход в состоянии pending не повторяется. Не создавайте новую игру по RESUME: возвращайте существующую или явную ошибку.

Клиент помнит matchId/roomId только до перезагрузки страницы. Сохранение этих ID или поиск текущей комнаты после перезагрузки — отдельный TODO. На сервере завершённый матч держите некоторое время доступным для RESUME, например 10 минут, затем удаляйте вместе с per-match lock и dedup-cache.

Авторизация по cookie требует корректной cookie-policy и Origin. Frontend не имеет поля bearer-token и не передаёт Authorization-заголовок WebSocket: если решите использовать JWT, потребуется отдельная доработка текущего клиента, а не только замена Spring-конфига.
