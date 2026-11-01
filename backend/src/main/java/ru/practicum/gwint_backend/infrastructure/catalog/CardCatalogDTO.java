package ru.practicum.gwint_backend.infrastructure.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.practicum.gwint_backend.domain.model.CardDefinition;
import ru.practicum.gwint_backend.domain.rules.CardCatalog;

import java.util.List;

public record CardCatalogDTO(@JsonProperty("cards")List<CardDefinition> cards){

}
