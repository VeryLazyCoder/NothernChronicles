package ru.practicum.gwint_backend;

import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;
import ru.practicum.gwint_backend.domain.model.*;
import ru.practicum.gwint_backend.domain.rules.DeckFactory;
import ru.practicum.gwint_backend.domain.rules.MatchInvariants;
import ru.practicum.gwint_backend.infrastructure.catalog.JsonCardCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;


class GwintBackendApplicationTests {
	private final Random randomWithTestingSeed = new Random(145);
	private final DeckFactory factory = new DeckFactory();
	@DisplayName("Проверка загрузки каталога карт")
	@Test
	void checkDeserialisingCardsFromJsonFile() {
		var loader = new JsonCardCatalog();
		var cards = loader.getAll();
		assertNotNull(cards);
		assertEquals(14,cards.size());
		assertTrue(loader.contains(new CardId("warden")));
	}

	@DisplayName("Проверка наличия ровно 30 кард в колоде в одинаковом порядке")
	@Test
	void checkCorrectDeckCreating(){
		var player1 = new PlayerId("player1");
		var deck = factory.createDeckForPlayer(player1);

		assertEquals(30, deck.size());

		var anotherDeck = factory.createDeckForPlayer(player1);
		assertEquals(deck, anotherDeck);
	}

	@DisplayName("Проверка корректного разделения руки и колоды")
	@Test
	void checkHandAndDeckAmount(){
		var player1 = new PlayerId("player1");
		var playerState = factory.createInitialPlayerState(player1, "John Doe", randomWithTestingSeed);

		assertEquals(10, playerState.hand().size());
		assertEquals(20, playerState.deck().size());
	}

	@DisplayName("Проверка детерминированности алгоритма перемешивания")
	@Test
	void checkFisherYatesLogic(){
		var player1 = new PlayerId("player1");
		var deck = factory.createDeckForPlayer(player1);
		var shuffled = factory.shuffle(deck, new Random(145));
		var anotherShuffled = factory.shuffle(deck, new Random(145));
		assertEquals(shuffled, anotherShuffled);

		var random = new Random(123);
		var shuffledWithAnotherSeed = factory.shuffle(deck, random);
		assertNotEquals(shuffled, shuffledWithAnotherSeed);
	}
	@DisplayName("Проверка неизменности состава карт")
	@Test
	void checkDeckContent(){
		var player1 = new PlayerId("player1");
		var deck = factory.createDeckForPlayer(player1);
		var setOfExpectedCards = new HashSet<CardInstance>(Set.of(
				new CardInstance(new CardId("warden"), new InstanceId("player1-warden-1")),
				new CardInstance(new CardId("warden"), new InstanceId("player1-warden-2")),
				new CardInstance(new CardId("warden"), new InstanceId("player1-warden-3")),

				new CardInstance(new CardId("rider"), new InstanceId("player1-rider-1")),
				new CardInstance(new CardId("rider"), new InstanceId("player1-rider-2")),
				new CardInstance(new CardId("rider"), new InstanceId("player1-rider-3")),

				new CardInstance(new CardId("wolf"), new InstanceId("player1-wolf-1")),
				new CardInstance(new CardId("wolf"), new InstanceId("player1-wolf-2")),
				new CardInstance(new CardId("wolf"), new InstanceId("player1-wolf-3")),

				new CardInstance(new CardId("archer"), new InstanceId("player1-archer-1")),
				new CardInstance(new CardId("archer"), new InstanceId("player1-archer-2")),
				new CardInstance(new CardId("archer"), new InstanceId("player1-archer-3")),

				new CardInstance(new CardId("scout"), new InstanceId("player1-scout-1")),
				new CardInstance(new CardId("scout"), new InstanceId("player1-scout-2")),
				new CardInstance(new CardId("scout"), new InstanceId("player1-scout-3")),

				new CardInstance(new CardId("crossbow"), new InstanceId("player1-crossbow-1")),
				new CardInstance(new CardId("crossbow"), new InstanceId("player1-crossbow-2")),
				new CardInstance(new CardId("crossbow"), new InstanceId("player1-crossbow-3")),

				new CardInstance(new CardId("catapult"), new InstanceId("player1-catapult-1")),
				new CardInstance(new CardId("catapult"), new InstanceId("player1-catapult-2")),
				new CardInstance(new CardId("catapult"), new InstanceId("player1-catapult-3")),

				new CardInstance(new CardId("engineer"), new InstanceId("player1-engineer-1")),
				new CardInstance(new CardId("engineer"), new InstanceId("player1-engineer-2")),
				new CardInstance(new CardId("engineer"), new InstanceId("player1-engineer-3")),

				new CardInstance(new CardId("hero"), new InstanceId("player1-hero-1")),
				new CardInstance(new CardId("frost"), new InstanceId("player1-frost-1")),
				new CardInstance(new CardId("rain"), new InstanceId("player1-rain-1")),
				new CardInstance(new CardId("fog"), new InstanceId("player1-fog-1")),
				new CardInstance(new CardId("clear"), new InstanceId("player1-clear-1")),
				new CardInstance(new CardId("horn"), new InstanceId("player1-horn-1"))
		));

		assertTrue(deck.containsAll(setOfExpectedCards));
	}

	@DisplayName("Проверка валидации корректного матча")
	@Test
	void checkPassingValidationWithCorrectRound(){
		var player1 = new PlayerId("player1");
		var player2 = new PlayerId("player2");
		var matchState = factory.createInitialMatch(new MatchId("Match 1"), player1, "Gamer1",
				player2, "Gamer2", new Random());

		MatchInvariants.validate(matchState, new JsonCardCatalog());
	}

	@DisplayName("Проверка выброса исключений на ошибки в состоянии матча")
	@Test
	void checkFailingValidationWithIncorrectRound(){
		var player1 = new PlayerId("player1");
		var player2 = new PlayerId("player2");
		var matchState = factory.createInitialMatch(new MatchId("Match 1"), player1, "Gamer1",
				player2, "Gamer2", new Random());
		var catalog = new JsonCardCatalog();

		// 1. В фазе PLAYING активный игрок не может быть null
		assertThrows(RuleViolationException.class, () ->
				MatchInvariants.validate(matchState.withActivePlayer(null), catalog));

		// 2. В фазе PLAYING активный игрок обязан быть среди участников матча
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withActivePlayer(new PlayerId("Player3")), catalog));

		// 3. В фазе ROUND_OVER активный игрок обязан быть null
		assertThrows(RuleViolationException.class, () ->
				MatchInvariants.validate(matchState.withPhase(MatchPhase.ROUND_OVER).withActivePlayer(player1), catalog));

		// 4. В фазе MATCH_OVER активный игрок обязан быть null
		assertThrows(RuleViolationException.class, () ->
				MatchInvariants.validate(matchState.withPhase(MatchPhase.MATCH_OVER).withActivePlayer(player1), catalog));

		// 5. Игроки с одинаковым id недопустимы
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withPlayers(List.of(matchState.players().get(0), matchState.players().get(0))), catalog));

		// 6. У игрока не 30 карт (например, 29 карт: убрали одну из руки)
		var p1LessCards = matchState.players().get(0).withHand(
				matchState.players().get(0).hand().subList(0, 9));
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withUpdatedPlayer(p1LessCards), catalog));

		// 7. Дубликат карты у одного игрока (карта одновременно в руке и на поле)
		var card = matchState.players().get(0).hand().getFirst();
		var p1DupCards = matchState.players().get(0).withBoard(
				Board.empty().withAddedCard(card, Row.MELEE));
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withUpdatedPlayer(p1DupCards), catalog));

		// 8. Пересечение карт между игроками (игрок 2 имеет карту игрока 1)
		var p2WithP1Card = matchState.players().get(1).withHand(
				Stream.concat(matchState.players().get(1).hand().stream().limit(9), Stream.of(card)).toList());
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withUpdatedPlayer(p2WithP1Card), catalog));

		// 9. Несуществующая карта (не из каталога)
		var illegalCard = new CardInstance(new CardId("cheat_card"), new InstanceId("player1-cheat-1"));
		var p1IllegalCard = matchState.players().get(0).withHand(
				Stream.concat(matchState.players().get(0).hand().stream().limit(9), Stream.of(illegalCard)).toList());
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withUpdatedPlayer(p1IllegalCard), catalog));

		// 10. Два рога в одном ряду у одного игрока
		var hornCard1 = new CardInstance(new CardId("horn"), new InstanceId("p1-horn-1"));
		var hornCard2 = new CardInstance(new CardId("horn"), new InstanceId("p1-horn-2"));
		var p1TwoHornsInMelee = matchState.players().get(0).withHorns(List.of(
				new HornPlacement(Row.MELEE, hornCard1),
				new HornPlacement(Row.MELEE, hornCard2)));
		assertThrows(IllegalArgumentException.class, () ->
				MatchInvariants.validate(matchState.withUpdatedPlayer(p1TwoHornsInMelee), catalog));

		// 11. Недопустимый раунд (round < 1 или round > 3)
		assertThrows(IllegalArgumentException.class, () ->
				matchState.withRound(0));
		assertThrows(IllegalArgumentException.class, () ->
				matchState.withRound(4));

		// 12. Недопустимое количество жизней (lives < 0 или lives > 2)
		assertThrows(IllegalArgumentException.class, () ->
				matchState.players().get(0).withLives(-1));
		assertThrows(IllegalArgumentException.class, () ->
				matchState.players().get(0).withLives(3));
	}
}