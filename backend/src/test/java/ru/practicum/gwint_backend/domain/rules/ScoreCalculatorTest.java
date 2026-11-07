package ru.practicum.gwint_backend.domain.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.gwint_backend.domain.model.Board;
import ru.practicum.gwint_backend.domain.model.CardDefinition;
import ru.practicum.gwint_backend.domain.model.CardId;
import ru.practicum.gwint_backend.domain.model.CardInstance;
import ru.practicum.gwint_backend.domain.model.CardType;
import ru.practicum.gwint_backend.domain.model.HornPlacement;
import ru.practicum.gwint_backend.domain.model.InstanceId;
import ru.practicum.gwint_backend.domain.model.PlayerId;
import ru.practicum.gwint_backend.domain.model.PlayerState;
import ru.practicum.gwint_backend.domain.model.Row;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {
    private static final CardId MELEE_FOUR_ID = new CardId("test-melee-four");
    private static final CardId MELEE_SIX_ID = new CardId("test-melee-six");
    private static final CardId MELEE_HERO_ID = new CardId("test-melee-hero");
    private static final CardId RANGED_THREE_ID = new CardId("test-ranged-three");
    private static final CardId SIEGE_TWO_ID = new CardId("test-siege-two");

    private ScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        var definitions = List.of(
                definition(MELEE_FOUR_ID, 4, Row.MELEE, false),
                definition(MELEE_SIX_ID, 6, Row.MELEE, false),
                definition(MELEE_HERO_ID, 10, Row.MELEE, true),
                definition(RANGED_THREE_ID, 3, Row.RANGED, false),
                definition(SIEGE_TWO_ID, 2, Row.SIEGE, false)
        );

        calculator = new ScoreCalculator(new InMemoryCardCatalog(definitions));
    }

    @Test
    void calculatesOrdinaryCardPowerWithoutEffects() {
        var player = playerWithBoard(List.of(card(MELEE_FOUR_ID)), List.of(), List.of());

        assertEquals(4, calculator.calculateCardPower(card(MELEE_FOUR_ID), player, Set.of()));
    }

    @Test
    void weatherReducesOrdinaryCardPowerToOne() {
        var player = playerWithBoard(List.of(card(MELEE_FOUR_ID)), List.of(), List.of());

        assertEquals(1, calculator.calculateCardPower(
                card(MELEE_FOUR_ID), player, Set.of(Row.MELEE)));
    }

    @Test
    void hornDoublesPowerAfterWeatherIsApplied() {
        var horn = new HornPlacement(Row.MELEE, card(new CardId("test-horn")));
        var player = playerWithBoard(List.of(card(MELEE_FOUR_ID)), List.of(), List.of(horn));

        assertEquals(2, calculator.calculateCardPower(
                card(MELEE_FOUR_ID), player, Set.of(Row.MELEE)));
    }

    @Test
    void heroIgnoresWeatherAndHorn() {
        var horn = new HornPlacement(Row.MELEE, card(new CardId("test-horn")));
        var player = playerWithBoard(List.of(card(MELEE_HERO_ID)), List.of(), List.of(horn));

        assertEquals(10, calculator.calculateCardPower(
                card(MELEE_HERO_ID), player, Set.of(Row.MELEE)));
    }

    @Test
    void hornOnlyBoostsThePlayerWhoOwnsIt() {
        var meleeCard = card(MELEE_FOUR_ID);
        var horn = new HornPlacement(Row.MELEE, card(new CardId("test-horn")));
        var owner = playerWithBoard(List.of(meleeCard), List.of(), List.of(horn));
        var opponent = playerWithBoard(List.of(meleeCard), List.of(), List.of());

        assertEquals(8, calculator.calculateCardPower(meleeCard, owner, Set.of()));
        assertEquals(4, calculator.calculateCardPower(meleeCard, opponent, Set.of()));
    }

    @Test
    void calculatesMeleeRowScoreWithWeatherAndHorn() {
        var meleeCards = List.of(
                card(MELEE_FOUR_ID),
                card(MELEE_SIX_ID),
                card(MELEE_HERO_ID)
        );
        var horn = new HornPlacement(Row.MELEE, card(new CardId("test-horn")));
        var player = playerWithBoard(meleeCards, List.of(), List.of(horn));

        assertEquals(30, calculator.calculateRowScore(player, Row.MELEE, Set.of()));
        assertEquals(14, calculator.calculateRowScore(player, Row.MELEE, Set.of(Row.MELEE)));
    }

    @Test
    void weatherOnlyChangesItsOwnRowAndPlayerScoreAddsAllRows() {
        var player = playerWithBoard(
                List.of(card(MELEE_FOUR_ID), card(MELEE_SIX_ID), card(MELEE_HERO_ID)),
                List.of(card(RANGED_THREE_ID)),
                List.of()
        );

        assertEquals(12, calculator.calculateRowScore(player, Row.MELEE, Set.of(Row.MELEE)));
        assertEquals(3, calculator.calculateRowScore(player, Row.RANGED, Set.of(Row.MELEE)));
        assertEquals(17, calculator.calculatePlayerScore(player, Set.of(Row.MELEE)));
    }

    private static CardDefinition definition(CardId id, int power, Row row, boolean hero) {
        return new CardDefinition(id, id.id(), "test card", "", power, row, CardType.UNIT, hero);
    }

    private static CardInstance card(CardId cardId) {
        return new CardInstance(cardId, new InstanceId("instance-" + cardId.id()));
    }

    private static PlayerState playerWithBoard(
            List<CardInstance> melee,
            List<CardInstance> ranged,
            List<HornPlacement> horns
    ) {
        var playerId = new PlayerId("player-1");
        var board = new Board(melee, ranged, List.of(card(SIEGE_TWO_ID)));
        return new PlayerState(
                playerId,
                "Test player",
                2,
                false,
                List.of(),
                List.of(),
                List.of(),
                board,
                horns
        );
    }

    private record InMemoryCardCatalog(Map<CardId, CardDefinition> cards) implements CardCatalog {
        private InMemoryCardCatalog(Collection<CardDefinition> definitions) {
            this(definitions.stream().collect(Collectors.toMap(CardDefinition::id, definition -> definition)));
        }

        @Override
        public CardDefinition get(CardId cardId) {
            return cards.get(cardId);
        }

        @Override
        public Optional<CardDefinition> getById(CardId cardId) {
            return Optional.ofNullable(cards.get(cardId));
        }

        @Override
        public boolean contains(CardId cardId) {
            return cards.containsKey(cardId);
        }

        @Override
        public Collection<CardDefinition> getAll() {
            return cards.values();
        }
    }
}
