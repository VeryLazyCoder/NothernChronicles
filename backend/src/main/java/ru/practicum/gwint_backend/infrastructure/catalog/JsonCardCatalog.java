package ru.practicum.gwint_backend.infrastructure.catalog;


import ru.practicum.gwint_backend.domain.model.CardDefinition;
import ru.practicum.gwint_backend.domain.model.CardId;
import ru.practicum.gwint_backend.domain.model.CardType;
import ru.practicum.gwint_backend.domain.model.Row;
import ru.practicum.gwint_backend.domain.rules.CardCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.*;

public class JsonCardCatalog implements CardCatalog {
    private List<CardDefinition> cards;
    private final Map<CardId, CardDefinition> cardMap;
    public JsonCardCatalog() {
        cards = loadCardsFromJson();
        if (!validateCards(cards))
            throw new IllegalStateException("Cards were not validated");

        cardMap = cards.stream().collect(Collectors.toMap(CardDefinition::id, cd -> cd));
    }
    @Override
    public CardDefinition get(CardId cardId) {
        return cardMap.get(cardId);
    }

    @Override
    public Optional<CardDefinition> getById(CardId cardId) {
        return cards.stream().filter(c -> c.id().equals(cardId)).findFirst();
    }

    @Override
    public boolean contains(CardId cardId) {
        return cardMap.containsKey(cardId);
    }

    @Override
    public Collection<CardDefinition> getAll() {
        return cards;
    }

    private List<CardDefinition> loadCardsFromJson() {
        var mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream("cards.json")) {
            if(inputStream == null)
                throw new IllegalArgumentException("cards.json file not found");
            return mapper.readValue(inputStream, CardCatalogDTO.class).cards();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("failed to load cards.json", e);
        }
    }

    ///
    /// @param cards list of cards to validate to business rules
    /// @return true if cards are valid
    private boolean validateCards(Collection<CardDefinition> cards) {
        if (cards.stream().anyMatch(c -> c.cardType() == CardType.UNIT && c.power() < 1))
            return false;

        var idSet = new HashSet<CardId>();
        if(!cards.stream().map(CardDefinition::id).allMatch(idSet::add))
            return false;

        return true;
    }
}
