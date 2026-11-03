package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;
import ru.practicum.gwint_backend.domain.model.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class MatchInvariants {
    private final static int numberOfCardsRequired = 30;

    public static boolean validate(MatchState state, CardCatalog catalog) throws RuleViolationException, IllegalArgumentException {
        var players = state.players();

        if ((state.phase().equals(MatchPhase.MATCH_OVER) || state.phase().equals(MatchPhase.ROUND_OVER))
                && state.activePlayerId() != null)
            throw new RuleViolationException(ErrorCode.INVALID_PHASE, "During this phase active player should be null");

        if (players.size() != 2 || players.get(0).id().equals(players.get(1).id()))
            throw new IllegalArgumentException("PlayersList must have two different players");
        if (players.stream().anyMatch(p -> p.lives() < 0 || p.lives() > 2))
            throw new IllegalArgumentException("Player's lives must be in range [0, 2]");
        if (state.round() < 1 || state.round() > 3)
            throw new IllegalArgumentException("Round must be between 1 and 3");

        if (state.phase().equals(MatchPhase.PLAYING)) {
            var activePlayer = state.activePlayerId();
            if (activePlayer == null)
                throw new RuleViolationException(ErrorCode.INVALID_PHASE, "During active playing phase active player can not be null");

            if (!players.stream().map(PlayerState::id).toList()
                    .contains(activePlayer))
                throw new IllegalArgumentException("Active player not in players list");
        }


        checkBothPlayersHaveRequiredLegalCardsAmount(players, catalog);
        checkCorrectHornPlacementForEachPlayers(players);
        return true;
    }

    private static void checkBothPlayersHaveRequiredLegalCardsAmount(List<PlayerState> players, CardCatalog catalog) {
        var player1AllCards = getAllPlayersCards(players.get(0));
        var player2AllCards = getAllPlayersCards(players.get(1));
        if (player1AllCards.size() != numberOfCardsRequired || player2AllCards.size() != numberOfCardsRequired)
            throw new IllegalArgumentException(String.format("Total number of cards must be %d", numberOfCardsRequired));

        if (!Collections.disjoint(player1AllCards, player2AllCards))
            throw new IllegalArgumentException("Players must have different cards");

        checkAllPlayerCardsUniqueAndLegal(player1AllCards, catalog);
        checkAllPlayerCardsUniqueAndLegal(player2AllCards, catalog);
    }

    private static List<CardInstance> getAllPlayersCards(PlayerState player) {
        return Stream.of(player.deck(), player.discardedCards(), player.hand(),
                        player.board().allCards(),
                        player.horns().stream().map(HornPlacement::card).toList())
                .flatMap(Collection::stream)
                .toList();
    }

    private static void checkAllPlayerCardsUniqueAndLegal(List<CardInstance> cards, CardCatalog catalog) {
        var cardsSet = new HashSet<>(cards);
        if (cardsSet.size() != cards.size())
            throw new IllegalArgumentException("Same cards have been found");

        if (cards.stream().anyMatch(c -> !catalog.contains(c.cardId())))
            throw new IllegalArgumentException("Unauthorized cards have been found");
    }

    private static void checkCorrectHornPlacementForEachPlayers(List<PlayerState> players) {
        var horns1 = players.get(0).horns();
        var horns2 = players.get(1).horns();

        checkCorrectHornPlacementForPlayer(horns1);
        checkCorrectHornPlacementForPlayer(horns2);
    }

    private static void checkCorrectHornPlacementForPlayer(List<HornPlacement> playerHorns) {
        var rows = new HashSet<>(playerHorns.stream().map(HornPlacement::row).toList());
        if (rows.size() != playerHorns.size())
            throw new IllegalArgumentException("Same rows have been found");
    }
}
