package ru.practicum.gwint_backend.domain.rules;
import org.junit.jupiter.api.*;
import org.springframework.lang.NonNull;
import ru.practicum.gwint_backend.domain.command.PassCommand;
import ru.practicum.gwint_backend.domain.command.PlayCardCommand;
import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;
import ru.practicum.gwint_backend.domain.model.*;
import ru.practicum.gwint_backend.infrastructure.catalog.JsonCardCatalog;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class GameEngineTests {
    private final DeckFactory deckFactory = new DeckFactory();
    private final CardCatalog catalog = new JsonCardCatalog();
    private final ScoreCalculator calculator = new ScoreCalculator(catalog);

    private GameEngine gameEngine;
    private MatchState match;
    private PlayerId  player1;
    private PlayerId player2;
    @BeforeEach
    public void setUp()
    {
        gameEngine = new GameEngine(catalog, calculator, deckFactory);
        player1 = new PlayerId("Player 1");
        player2 = new PlayerId("Player 2");
        match = gameEngine.start(new MatchId("Test match"), player1,
                player2, "Geralt","Yen", new Random(67), player1);
    }
    @Test
    @DisplayName("Проверка корректности ходов для всех 4-х типов карт и возможности совершать несколько ходов после паса соперника")
    void checkCorrectActionsAndPossibilityToActMultipleTimesAfterOpponentPassed()
    {
        match = gameEngine.apply(match, player1, new PassCommand());
        assertEquals(1, match.revision());
        assertTrue(match.players().get(0).isPassed());

        match = ensureInHand(new CardId("wolf"), match, player2);
        var instanceId = getCardInstanceIdFromPlayerHand(new CardId("wolf"), match, player2);
        match = gameEngine.apply(match, player2, new PlayCardCommand(instanceId, Row.MELEE));
        var p2 = getPlayer(match, player2);
        assertEquals(2, match.revision());
        assertEquals(9, p2.hand().size());
        assertEquals(1, p2.board().totalSize());
        assertTrue(p2.board().meleeRow().contains(new CardInstance(new CardId("wolf"), instanceId)));

        match = playCard(match, player2, "horn", Row.MELEE);
        p2 = getPlayer(match, player2);
        assertEquals(3, match.revision());
        assertEquals(8, p2.hand().size());
        assertEquals(1, p2.board().totalSize());
        assertEquals(1, p2.horns().size());
        assertEquals(Row.MELEE, p2.horns().get(0).row());

        match = playCard(match, player2, "frost", null);
        p2 = getPlayer(match, player2);
        assertEquals(4, match.revision());
        assertEquals(7, p2.hand().size());
        assertEquals(1, p2.discardedCards().size());
        assertEquals(1, match.weather().size());
        assertTrue(match.weather().contains(Row.MELEE));

        match = playCard(match, player2, "clear", null);
        p2 = getPlayer(match, player2);
        assertEquals(5, match.revision());
        assertEquals(6, p2.hand().size());
        assertEquals(2, p2.discardedCards().size());
        assertEquals(0, match.weather().size());
    }

    @DisplayName("Проверка автопаса после розыгрыша всех карт")
    @Test
    void checkAutoPassWithEmptyHand()
    {
        match = gameEngine.apply(match, player1, new PassCommand());
        match = playCard(match, player2, "wolf", Row.MELEE);
        match = playCard(match, player2, "wolf", Row.MELEE);
        match = playCard(match, player2, "wolf", Row.MELEE);
        match = playCard(match, player2, "rider", Row.MELEE);
        match = playCard(match, player2, "rider", Row.MELEE);
        match = playCard(match, player2, "rider", Row.MELEE);
        match = playCard(match, player2, "warden", Row.MELEE);
        match = playCard(match, player2, "warden", Row.MELEE);
        match = playCard(match, player2, "warden", Row.MELEE);
        match = playCard(match, player2, "catapult", Row.SIEGE);
        var p2 = getPlayer(match, player2);
        assertTrue(p2.isPassed());
        assertNull(match.activePlayerId());
    }

    @DisplayName("Проверка выброса ошибки при попытке пойти не в свой ход")
    @Test
    void checkRaisingNotYourTurnException()
    {
        var exception = assertThrows(RuleViolationException.class, () -> gameEngine.apply(match, player2, new PassCommand()));
        assertEquals(ErrorCode.NOT_YOUR_TURN, exception.getErrorCode());
    }

    @DisplayName("Проверка выброса ошибки при попытке сыграть не свою карту")
    @Test
    void checkRaisingNotYourCardException()
    {
        var exception = assertThrows(RuleViolationException.class,
                () -> gameEngine.apply(match, player1, new PlayCardCommand(new InstanceId("unexisting"), Row.MELEE)));
        assertEquals(ErrorCode.CARD_NOT_IN_HAND, exception.getErrorCode());
    }

    @DisplayName("Проверка выброса ошибки при попытке сыграть ясное небо на стол без погоды. Изначальное состояние матча " +
            "не должно измениться")
    @Test
    void checkRaisingNothingWeatherToClearException()
    {
        var card = new CardId("clear");
        match = ensureInHand(card, match, player1);
        var originalMatch = match;
        var instanceId = getCardInstanceIdFromPlayerHand(card, match, player1);
        var exception = assertThrows(RuleViolationException.class,
                () -> gameEngine.apply(match, player1, new PlayCardCommand(instanceId, null)));
        assertEquals(ErrorCode.INVALID_TARGET, exception.getErrorCode());
        assertEquals(originalMatch, match);
    }

    @DisplayName("Проверка выброса ошибки при попытке сыграть одну и ту же погоду")
    @Test
    void checkRaisingAlreadyContainsWeatherException()
    {
        var card = new CardId("frost");
        match = ensureInHand(card, match, player1);
        var instanceId1 = getCardInstanceIdFromPlayerHand(card, match, player1);
        match = ensureInHand(card, match, player2);
        var instanceId2 = getCardInstanceIdFromPlayerHand(card, match, player2);
        match = gameEngine.apply(match, player1, new PlayCardCommand(instanceId1, Row.MELEE));
        var exception = assertThrows(RuleViolationException.class,
                () -> gameEngine.apply(match, player2, new PlayCardCommand(instanceId2, Row.MELEE)));
        assertEquals(ErrorCode.EFFECT_ALREADY_ACTIVE, exception.getErrorCode());
    }

    @DisplayName("Проверка выброса ошибки при попытке выставить карту не в свой ряд")
    @Test
    void checkRaisingInvalidRowException()
    {
        var card = new CardId("wolf");
        match = ensureInHand(card, match, player1);
        var instanceId = getCardInstanceIdFromPlayerHand(card, match, player1);
        var exception = assertThrows(RuleViolationException.class,
                () -> gameEngine.apply(match, player1, new PlayCardCommand(instanceId, Row.RANGED)));
        assertEquals(ErrorCode.INVALID_TARGET, exception.getErrorCode());
    }
    private MatchState playCard(MatchState state, PlayerId playerId, String cardName, Row row) {
        var cardId = new CardId(cardName);
        var stateWithCard = ensureInHand(cardId, state, playerId);
        var instanceId = getCardInstanceIdFromPlayerHand(cardId, stateWithCard, playerId);
        return gameEngine.apply(stateWithCard, playerId, new PlayCardCommand(instanceId, row));
    }

    private MatchState ensureInHand(CardId cardToEnsure, MatchState state, PlayerId playerId){
        var player = getPlayer(state, playerId);

        if (player.hand().stream().anyMatch(cd -> cd.cardId().equals(cardToEnsure)))
            return state;

        var cardToReplaceFromDeck = player.deck().stream().filter(cd -> cd.cardId().equals(cardToEnsure))
                .findFirst().orElseThrow();
        var newDeck = new ArrayList<>(player.deck());
        newDeck.remove(cardToReplaceFromDeck);
        var newHand = new ArrayList<>(player.hand());
        var cardToReplaceFromHand = newHand.removeFirst();
        newHand.add(cardToReplaceFromDeck);
        newDeck.add(cardToReplaceFromHand);

        player = player.withHand(newHand).withDeck(newDeck);
        return state.withUpdatedPlayer(player);
    }

    @NonNull
    private static PlayerState getPlayer(MatchState state, PlayerId playerId) {
        return state.players().stream()
                .filter(p -> p.id().equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    private InstanceId getCardInstanceIdFromPlayerHand(CardId cardToFind, MatchState state, PlayerId playerId)
    {
        var player = getPlayer(state, playerId);

        return player.hand().stream()
                .filter(c -> c.cardId().equals(cardToFind))
                .findFirst()
                .orElseThrow()
                .instanceId();
    }
}
