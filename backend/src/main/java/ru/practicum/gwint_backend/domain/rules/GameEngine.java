package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.command.DomainCommand;
import ru.practicum.gwint_backend.domain.command.PassCommand;
import ru.practicum.gwint_backend.domain.command.PlayCardCommand;
import ru.practicum.gwint_backend.domain.command.ReadyNextRoundCommand;
import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;
import ru.practicum.gwint_backend.domain.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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

    public MatchState apply(MatchState state, PlayerId actor, DomainCommand command){
        MatchInvariants.validate(state, catalog);
        if (state.phase() != MatchPhase.PLAYING)
            throw new RuleViolationException(ErrorCode.INVALID_PHASE, "You should be PLAYING");

        var player = state.players().stream().filter(p -> p.id().equals(actor)).findFirst()
                .orElseThrow(() -> new RuleViolationException(ErrorCode.FORBIDDEN, "You are not a member of this match"));
        if (!state.activePlayerId().equals(actor))
            throw new RuleViolationException(ErrorCode.NOT_YOUR_TURN, "Active player is your opponent");

        if (player.isPassed())
            throw new RuleViolationException(ErrorCode.ALREADY_PASSED, "You already passed");

        var newState = switch (command){
            case PlayCardCommand playCardCommand ->
                    playCardCommandHandler(state, player, playCardCommand.cardInstanceId(), playCardCommand.row());
            case PassCommand passCommand -> passCommandHandler(state, player);
            case ReadyNextRoundCommand reReadyNextRoundCommand -> readyNextRoundCommandHandler(state, player);
        };
        var otherPlayer = state.players().stream().filter(p -> p.id().equals(player.id()) == false).findFirst().get();
        if (otherPlayer.isPassed()){
            if (newState.players().stream().allMatch(PlayerState::isPassed))
                newState = newState.withPhase(MatchPhase.ROUND_OVER).withActivePlayer(null);
            else
                newState = newState.withActivePlayer(player.id());
        }
        else
            newState = newState.withActivePlayer(otherPlayer.id());
        MatchInvariants.validate(newState, catalog);
        return newState.withRevision(state.revision() + 1);
    }

    private MatchState playCardCommandHandler(MatchState state, PlayerState player, InstanceId cardInstanceId, Row row){
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
        if (player.hand().isEmpty()){
            player = player.withPassed(true);
            state = state.withLogsAddRow(String.format("[%s]: пас до конца раунда", player.id().id()));
        }
        return state.withUpdatedPlayer(player);
    }

    private MatchState passCommandHandler(MatchState state, PlayerState player){
        player = player.withPassed(true);
        state = state.withLogsAddRow(String.format("[%s]: пас до конца раунда", player.id().id()));
        return state.withUpdatedPlayer(player);
    }

    private MatchState readyNextRoundCommandHandler(MatchState state, PlayerState player){
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
