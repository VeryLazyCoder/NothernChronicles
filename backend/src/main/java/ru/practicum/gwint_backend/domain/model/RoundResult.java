package ru.practicum.gwint_backend.domain.model;

import java.util.List;
import java.util.Optional;

public record RoundResult(Optional<PlayerId> winner, List<Integer> scores, int round) {
}
