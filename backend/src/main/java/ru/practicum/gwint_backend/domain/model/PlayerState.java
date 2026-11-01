package ru.practicum.gwint_backend.domain.model;

import ru.practicum.gwint_backend.domain.error.ErrorCode;
import ru.practicum.gwint_backend.domain.error.RuleViolationException;

import java.util.ArrayList;
import java.util.List;

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
    }

    public PlayerState withLives(int newLives) {
        return new PlayerState(id, name, newLives, isPassed, hand, deck, discardedCards, board, horns);
    }

    public PlayerState withPassed(boolean newPassed) {
        return new PlayerState(id, name, lives, newPassed, hand, deck, discardedCards, board, horns);
    }

    public PlayerState withCardPlayed(CardInstance cardPlayed) {
        if (hand.contains(cardPlayed))
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "This card have not found in player's hand");

        var newHand = new ArrayList<>(hand);
        newHand.remove(cardPlayed);
        return new PlayerState(id, name, lives, isPassed, newHand, deck, discardedCards, board, horns);
    }

    public PlayerState withAddedCardToHand(CardInstance card) {
        var newHand = new ArrayList<>(hand);
        newHand.add(card);

        return new PlayerState(id, name, lives, isPassed, newHand, deck, discardedCards, board, horns);
    }

    public PlayerState withCardDiscarded(CardInstance cardPlayed) {
        if (hand.contains(cardPlayed))
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "This card have not found in player's hand");

        var newHand = new ArrayList<>(hand);
        newHand.remove(cardPlayed);
        var newDiscardedCards = new ArrayList<CardInstance>(discardedCards);
        discardedCards.remove(cardPlayed);
        return new PlayerState(id, name, lives, isPassed, newHand, deck, newDiscardedCards, board, horns);
    }

    public PlayerState withTakenCardFromDeck() {
        var newHand = new ArrayList<>(hand);
        if (deck.isEmpty())
            throw new RuleViolationException(ErrorCode.CARD_NOT_IN_HAND, "Deck is empty");
        var cardToTake = deck.getFirst();
        newHand.add(cardToTake);

        return new PlayerState(id, name, lives, isPassed, newHand, deck, discardedCards, board, horns);
    }

    public PlayerState withHornPlacement(HornPlacement horn) {
        var newHornPlacements = new ArrayList<HornPlacement>(horns);
        horns.add(horn);

        return new PlayerState(id, name, lives, isPassed, hand, deck, discardedCards, board, newHornPlacements);
    }
}
