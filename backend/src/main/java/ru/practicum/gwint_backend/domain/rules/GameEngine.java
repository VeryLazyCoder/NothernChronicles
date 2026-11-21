package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.command.*;
import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;
import ru.practicum.gwint_backend.domain.model.*;

import java.util.*;
import java.util.random.RandomGenerator;

public class GameEngine {
    private final CardCatalog catalog;
    private final ScoreCalculator calculator;
    private final DeckFactory factory;

    public GameEngine(CardCatalog catalog, ScoreCalculator calculator, DeckFactory factory) {
        this.catalog = catalog;
        this.calculator = calculator;
        this.factory = factory;
    }

    public MatchState start(MatchId matchId, PlayerId p1, PlayerId p2, String name1,
                            String name2, RandomGenerator random, PlayerId activePlayer) {
        return factory.createInitialMatch(matchId, p1, name1, p2, name2, random, activePlayer);
    }

    public MatchState apply(MatchState state, PlayerId actor, DomainCommand command) {
        MatchInvariants.validate(state, catalog);
        var player = state.players().stream().filter(p -> p.id().equals(actor)).findFirst()
                .orElseThrow(() -> new RuleViolationException(ErrorCode.FORBIDDEN, "You are not a member of this match"));

        if (command instanceof ReadyNextRoundCommand)
            return validateAndReturnMatchState(readyNextRoundCommandHandler(state, player));

        if (state.phase() != MatchPhase.PLAYING)
            throw new RuleViolationException(ErrorCode.INVALID_PHASE, "You should be PLAYING");

        if (!state.activePlayerId().equals(actor))
            throw new RuleViolationException(ErrorCode.NOT_YOUR_TURN, "Active player is your opponent");

        if (player.isPassed())
            throw new RuleViolationException(ErrorCode.ALREADY_PASSED, "You already passed");

        var newState = switch ((InRoundCommand)command) {
            case PlayCardCommand playCardCommand ->
                    playCardCommandHandler(state, player, playCardCommand.cardInstanceId(), playCardCommand.row());
            case PassCommand passCommand -> passCommandHandler(state, player);
        };
        var otherPlayer = state.players().stream().filter(p -> p.id().equals(player.id()) == false).findFirst().get();
        if (otherPlayer.isPassed()) {
            if (newState.players().stream().allMatch(PlayerState::isPassed))
                newState = finishRound(newState);
            else
                newState = newState.withActivePlayer(player.id());
        }
        else
            newState = newState.withActivePlayer(otherPlayer.id());
        return validateAndReturnMatchState(newState);
    }

    private MatchState validateAndReturnMatchState(MatchState state) {
        MatchInvariants.validate(state, catalog);
        return state.withRevision(state.revision() + 1);
    }

    private MatchState playCardCommandHandler(MatchState state, PlayerState player, InstanceId cardInstanceId, Row row) {
        var optionalCardInstance = player.hand().stream().filter(c -> c.instanceId().equals(cardInstanceId)).findFirst();
        if (optionalCardInstance.isEmpty())
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "Card is not in hand");
        var cardInstance = optionalCardInstance.get();
        var cardDefinition = catalog.get(cardInstance.cardId());

        switch (cardDefinition.cardType()) {
            case UNIT -> {
                if (row == null)
                    throw new RuleViolationException(ErrorCode.INVALID_TARGET, "Row to place unit card can't be null");
                if (!row.equals(cardDefinition.row()))
                    throw new RuleViolationException(ErrorCode.INVALID_TARGET, "Row to place does not match with available rows for the card");
                player = player.withCardPlayedToBoard(cardInstance, row);
            }
            case HORN -> {
                if (row == null)
                    throw new RuleViolationException(ErrorCode.INVALID_TARGET, "Row to place horn card can't be null");
                var hornPlacement = new HornPlacement(row, cardInstance);
                if (player.horns().stream().anyMatch(hp -> hp.row().equals(row)))
                    throw new RuleViolationException(ErrorCode.EFFECT_ALREADY_ACTIVE, "You already have horn placed in this row");
                player = player.withHornPlaced(hornPlacement);
            }
            case WEATHER -> {
                var weatherRow = cardDefinition.row();
                if (state.weather().contains(weatherRow))
                    throw new RuleViolationException(ErrorCode.EFFECT_ALREADY_ACTIVE, "Weather is already active in this row");
                var currentWeather = new HashSet<>(state.weather());
                currentWeather.add(weatherRow);
                player = player.withCardDiscarded(cardInstance);
                state = state.withWeather(currentWeather);
            }
            case CLEAR -> {
                if (state.weather().isEmpty())
                    throw new RuleViolationException(ErrorCode.INVALID_TARGET, "There is no weather in any row");
                state = state.withWeather(new HashSet<>());
                player = player.withCardDiscarded(cardInstance);
            }
        }
        state = state.withLogsAddRow(String.format("[%s]: [%s]", player.id().id(), cardDefinition.name()));
        if (player.hand().isEmpty()) {
            player = player.withPassed(true);
            state = state.withLogsAddRow(String.format("[%s]: пас до конца раунда", player.id().id()));
        }
        return state.withUpdatedPlayer(player);
    }

    private MatchState passCommandHandler(MatchState state, PlayerState player) {
        player = player.withPassed(true);
        state = state.withLogsAddRow(String.format("[%s]: пас до конца раунда", player.id().id()));
        return state.withUpdatedPlayer(player);
    }

    private MatchState readyNextRoundCommandHandler(MatchState state, PlayerState player) {
        if (!state.phase().equals(MatchPhase.ROUND_OVER))
            throw new RuleViolationException(ErrorCode.INVALID_PHASE, "Round must be over, but not the game entirely");
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private MatchState finishRound(MatchState state) {
        var roundResult = getRoundResult(state);
        var results = new ArrayList<>(state.results());
        results.add(roundResult);
        state = state.withResults(results);

        for (var player : state.players()) {
            if (roundResult.winner().isEmpty() || !roundResult.winner().get().equals(player.id())) {
                player = player.withLives(Math.max(0, player.lives() - 1));
                state = state.withUpdatedPlayer(player);
            }
        }

        if (state.players().stream().anyMatch(p -> p.lives() == 0)) {
            state = state.withPhase(MatchPhase.MATCH_OVER)
                    .withOutcome(MatchOutcome.COMPLETED)
                    .withActivePlayer(null);

            PlayerId winner = null;
            if (!state.players().stream().allMatch(p -> p.lives() == 0)) {
                var player1 = state.players().get(0);
                var player2 = state.players().get(1);
                winner = player1.lives() > 0? player1.id() : player2.id();
            }
            state = state.withWinner(winner);
            state = state.withLogsAddRow(String.format("Раунд %d: %d : %d. %s", roundResult.round(), roundResult.scores().get(0),
                    roundResult.scores().get(1), getLogOutcomeText(state.players(), roundResult.winner().orElse(null))))
                    .withLogsAddRow(String.format("Матч окончен. %s", getLogOutcomeText(state.players(), winner)));
        }
        else{
            state = state.withPhase(MatchPhase.ROUND_OVER)
                    .withActivePlayer(null)
                    .withReady(Set.of())
                    .withLogsAddRow(String.format("Раунд %d: %d : %d. %s", roundResult.round(), roundResult.scores().get(0),
                            roundResult.scores().get(1), getLogOutcomeText(state.players(), roundResult.winner().orElse(null))));

        }

        return state;
    }

    private RoundResult getRoundResult(MatchState state) {
        var player1 = state.players().get(0);
        var player2 = state.players().get(1);
        var score1 = calculator.calculatePlayerScore(player1, state.weather());
        var score2 = calculator.calculatePlayerScore(player2, state.weather());

        var winner = Optional.<PlayerId>empty();
        if (score1 > score2)
            winner = Optional.of(player1.id());
        if (score2 > score1)
            winner = Optional.of(player2.id());

        return new RoundResult(winner, List.of(score1, score2), state.round());
    }

    private String getLogOutcomeText(List<PlayerState> players, PlayerId winner) {
        if (winner == null)
            return "Ничья. Оба проиграли";
        var winnerPlayer = players.stream().filter(p -> p.id().equals(winner)).findFirst().get();
        return "Победил " + winnerPlayer.name();
    }
}
