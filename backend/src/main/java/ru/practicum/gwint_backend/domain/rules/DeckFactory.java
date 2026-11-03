package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.model.*;
import ru.practicum.gwint_backend.infrastructure.catalog.JsonCardCatalog;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeckFactory {
    private Collection<CardDefinition> cards;

    public DeckFactory(CardCatalog loader) {
        cards = loader.getAll();
    }

    public DeckFactory(){
        this(new JsonCardCatalog());
    }

    public List<CardInstance> shuffle(List<CardInstance> cards, RandomGenerator random) {
        var cardCollection = new ArrayList<CardInstance>(cards);
        Collections.shuffle(cardCollection, random);
        return List.copyOf(cardCollection);
    }

    public PlayerState createInitialPlayerState(PlayerId playerId, String name, RandomGenerator random) {
        var deck = shuffle(createDeckForPlayer(playerId), random);
        var hand = new ArrayList<>(deck.subList(0, 10));

        var result = new PlayerState(playerId, name, 2, false, hand,
                deck.stream().skip(10).toList(),
                new ArrayList<>(),
                Board.empty(), new ArrayList<>());
        return result;
    }

    public MatchState createInitialMatch(MatchId id, PlayerId p1, String name1, PlayerId p2, String name2, RandomGenerator random) {
        var player1 = createInitialPlayerState(p1, name1, random);
        var player2 = createInitialPlayerState(p2, name2, random);
        var players = List.of(player1, player2);
        var startingPlayer = players.get(random.nextInt(players.size())).id();

        var match = new MatchState(id, 0, MatchPhase.PLAYING, 1, startingPlayer, startingPlayer,
                players, new HashSet<>(),
                new HashSet<>(),
                new ArrayList<>(), null, List.of("Матч начался"));

        return match;
    }

    public List<CardInstance> createDeckForPlayer(PlayerId playerId) {
        var unitsOnly = cards.stream().filter(cd -> cd.cardType().equals(CardType.UNIT)
                && !cd.isHero()).toList();

        return Stream.of(createCardSequence(playerId, cards, 1),
                        createCardSequence(playerId, unitsOnly, 2),
                        createCardSequence(playerId, unitsOnly, 3)
                ).flatMap(Collection::stream)
                .toList();
    }

    private ArrayList<CardInstance> createCardSequence(PlayerId playerId, Collection<CardDefinition> cards, int seq) {
        return cards.stream().map(cd -> new CardInstance(cd.id(),
                        new InstanceId("%s-%s-%d".formatted(playerId.id(), cd.id().id(), seq))))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
