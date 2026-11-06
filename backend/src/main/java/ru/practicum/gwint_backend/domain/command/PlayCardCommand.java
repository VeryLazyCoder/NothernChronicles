package ru.practicum.gwint_backend.domain.command;

import ru.practicum.gwint_backend.domain.model.CardInstance;
import ru.practicum.gwint_backend.domain.model.InstanceId;
import ru.practicum.gwint_backend.domain.model.Row;

public record PlayCardCommand(InstanceId cardInstanceId, Row row) implements DomainCommand{
    public PlayCardCommand{
        if (cardInstanceId == null) throw new IllegalArgumentException("cardInstanceId cannot be null");
    }
}
