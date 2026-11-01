package ru.practicum.gwint_backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;

public record CardDefinition(@JsonProperty("cardId") CardId id,
                             String name,
                             String flavor,
                             @JsonProperty("art") String pathToArt,
                             int power,
                             Row row,
                             @JsonProperty("type") CardType cardType,
                             @JsonProperty("hero") boolean isHero) {
    public CardDefinition{
        if (power < 0 || power > 15)
            throw new RuleViolationException(ErrorCode.FORBIDDEN ,"power must be between 0 and 15");

        if (id == null)
            throw new NullPointerException("id must not be null");
        if ((cardType == CardType.CLEAR || cardType == CardType.HORN) && row != null)
            throw new IllegalArgumentException("Clear and horn types must have null row");
        if ((cardType == CardType.UNIT || cardType == CardType.WEATHER) && row == null)
            throw new IllegalArgumentException("Units and weather types must have correct row");
        if (cardType == null)
            throw new NullPointerException("cardType must not be null");
        if (name == null)
            throw new NullPointerException("name must not be null");
        if (flavor == null)
            throw new NullPointerException("flavor must not be null");
    }
}
