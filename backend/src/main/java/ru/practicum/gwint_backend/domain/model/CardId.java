package ru.practicum.gwint_backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record CardId(@JsonValue String id) {
    @JsonCreator
    public CardId(String id) {
        this.id = id;
    }
}
