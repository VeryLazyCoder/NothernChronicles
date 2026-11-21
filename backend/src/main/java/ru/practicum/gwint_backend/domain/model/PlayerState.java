package ru.practicum.gwint_backend.domain.model;

import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PlayerState(
        PlayerId id,
        String name,
        int lives,
        boolean isPassed,
        List<CardInstance> hand,
        List<CardInstance> deck,
        List<CardInstance> discardedCards,
        Board board,
        List<HornPlacement> horns) {
    public PlayerState {
        if (lives < 0 || lives > 2) {
            throw new IllegalArgumentException("Lives must be between 0 and 2");
        }
        hand = List.copyOf(hand);
        deck = List.copyOf(deck);
        discardedCards = List.copyOf(discardedCards);
        horns = List.copyOf(horns);

        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(board, "board must not be null");
    }

    public PlayerState withLives(int newLives) {
        return new PlayerState(id, name, newLives, isPassed, hand, deck, discardedCards, board, horns);
    }

    public PlayerState withPassed(boolean newPassed) {
        return new PlayerState(id, name, lives, newPassed, hand, deck, discardedCards, board, horns);
    }

    public PlayerState withHand(List<CardInstance> newHand) {
        return new PlayerState(id, name, lives, isPassed, newHand, deck, discardedCards, board, horns);
    }

    public PlayerState withDeck(List<CardInstance> newDeck) {
        return new PlayerState(id, name, lives, isPassed, hand, newDeck, discardedCards, board, horns);
    }

    public PlayerState withDiscardedCards(List<CardInstance> newDiscardedCards) {
        return new PlayerState(id, name, lives, isPassed, hand, deck, newDiscardedCards, board, horns);
    }

    public PlayerState withBoard(Board newBoard) {
        return new PlayerState(id, name, lives, isPassed, hand, deck, discardedCards, newBoard, horns);
    }
    public PlayerState withHornPlacement(HornPlacement horn) {
        var newHornPlacements = new ArrayList<HornPlacement>(horns);
        newHornPlacements.add(horn);

        return new PlayerState(id, name, lives, isPassed, hand, deck, discardedCards, board, newHornPlacements);
    }

    public PlayerState withHorns(List<HornPlacement> newHorns) {
        return new PlayerState(id, name, lives, isPassed, hand, deck, discardedCards, board, newHorns);
    }

    public PlayerState withCardPlayedToBoard(CardInstance cardPlayed, Row row) {
        if (!hand.contains(cardPlayed))
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "This card have not been found in player's hand");

        var newHand = new ArrayList<>(hand);
        newHand.remove(cardPlayed);
        return new PlayerState(id, name, lives, isPassed, newHand, deck, discardedCards,
                board.withAddedCard(cardPlayed, row), horns);
    }

    public PlayerState withHornPlaced(HornPlacement horn){
        var hornInstance = horn.card();
        if (!hand.contains(hornInstance))
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "This card have not been found in player's hand");

        var newHand = new ArrayList<>(hand);
        newHand.remove(hornInstance);
        return withHornPlacement(horn).withHand(newHand);
    }

    public PlayerState withCardDiscarded(CardInstance cardPlayed) {
        if (!hand.contains(cardPlayed))
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "This card have not been found in player's hand");

        var newHand = new ArrayList<>(hand);
        newHand.remove(cardPlayed);
        var newDiscardedCards = new ArrayList<CardInstance>(discardedCards);
        newDiscardedCards.add(cardPlayed);
        return new PlayerState(id, name, lives, isPassed, newHand, deck, newDiscardedCards, board, horns);
    }

    public PlayerState withRoundEndCleanup(){
        var newDiscarded = new ArrayList<>(discardedCards);
        newDiscarded.addAll(board.allCards());
        newDiscarded.addAll(horns.stream().map(HornPlacement::card).toList());
        return withDiscardedCards(newDiscarded)
                .withBoard(Board.empty())
                .withHorns(List.of())
                .withPassed(hand.isEmpty());
    }
}
