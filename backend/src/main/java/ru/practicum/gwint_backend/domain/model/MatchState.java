package ru.practicum.gwint_backend.domain.model;

import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MatchState(MatchId id,
                         long revision,
                         MatchPhase phase,
                         int round,
                         PlayerId starterId,
                         PlayerId activePlayerId,
                         List<PlayerState> players,
                         Set<Row> weather,
                         Set<PlayerId> ready,
                         List<RoundResult> results,
                         PlayerId winner,
                         List<String> logs) {

    public MatchState{
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(starterId, "starterId must not be null");
        Objects.requireNonNull(players, "players must not be null");

        if (round < 1 || round > 3)
            throw new IllegalArgumentException("Round must be between 1 and 3");
        if (players.size() != 2) {
            throw new IllegalArgumentException("Match must have exactly 2 players");
        }

        players = List.copyOf(players);
        weather = Set.copyOf(weather);
        ready = Set.copyOf(ready);
        results = List.copyOf(results);
        logs = List.copyOf(logs);

    }

    public MatchState withRevision(long newRevision) {
        return new MatchState(id, newRevision, phase, round, starterId, activePlayerId, players, weather, ready, results, winner, logs);
    }
    public MatchState withPhase(MatchPhase newPhase) {
        return new MatchState(id, revision, newPhase, round, starterId, activePlayerId, players, weather, ready, results, winner, logs);
    }
    public MatchState withRound(int newRound) {
        return new MatchState(id, revision, phase, newRound, starterId, activePlayerId, players, weather, ready, results, winner, logs);
    }
    public MatchState withStarter(PlayerId newStarter) {
        return new MatchState(id, revision, phase, round, newStarter, activePlayerId, players, weather, ready, results, winner, logs);
    }
    public MatchState withActivePlayer(PlayerId newActivePlayer) {
        return new MatchState(id, revision, phase, round, starterId, newActivePlayer, players, weather, ready, results, winner, logs);
    }
    public MatchState withPlayers(List<PlayerState> newPlayers) {
        return new MatchState(id, revision, phase, round, starterId, activePlayerId, newPlayers, weather, ready, results, winner, logs);
    }
    public MatchState withWeather(Set<Row> newWeather) {
        return new MatchState(id, revision, phase, round, starterId, activePlayerId, players, newWeather, ready, results, winner, logs);
    }
    public MatchState withReady(Set<PlayerId> newReady) {
        return new MatchState(id, revision, phase, round, starterId, activePlayerId, players, weather, newReady, results, winner, logs);
    }
    public MatchState withResults(List<RoundResult> newResults) {
        return new MatchState(id, revision, phase, round, starterId, activePlayerId, players, weather, ready, newResults, winner, logs);
    }
    public MatchState withWinner(PlayerId newWinner) {
        return new MatchState(id, revision, phase, round, starterId, activePlayerId, players, weather, ready, results, newWinner, logs);
    }
    public MatchState withLogsAddRow(String row) {
        var newLogs = new ArrayList<String>(logs);
        newLogs.add(row);
        if (newLogs.size() > 40)
            newLogs.removeFirst();

        return new MatchState(id, revision, phase, round, starterId, activePlayerId, players, weather, ready, results, winner, newLogs);
    }
    public MatchState withUpdatedPlayer(PlayerState newPlayer){
        var newPlayers = new ArrayList<PlayerState>(players);
        newPlayers.replaceAll(ps -> ps.id().equals(newPlayer.id())? newPlayer : ps);

        return withPlayers(newPlayers);
    }
}
