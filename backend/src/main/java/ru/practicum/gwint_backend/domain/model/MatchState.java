package ru.practicum.gwint_backend.domain.model;

import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;

import java.util.List;
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
        if (round < 1 || round > 3)
            throw new RuleViolationException(ErrorCode.INVALID_PHASE, "Only 3 rounds are possible.");
    }

}
