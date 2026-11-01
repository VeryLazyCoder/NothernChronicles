package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.model.CardDefinition;
import ru.practicum.gwint_backend.domain.model.CardId;
import java.util.Collection;
import java.util.Optional;

public interface CardCatalog {
    CardDefinition get(CardId cardId);

    Optional<CardDefinition> getById(CardId cardId);

    boolean contains(CardId cardId);

    Collection<CardDefinition> getAll();
}
