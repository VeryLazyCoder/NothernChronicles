package ru.practicum.gwint_backend.domain.rules;

import jdk.jshell.spi.ExecutionControl;
import ru.practicum.gwint_backend.domain.model.*;

import java.nio.channels.NotYetConnectedException;
import java.util.List;
import java.util.Set;

public class ScoreCalculator {
    private final CardCatalog catalog;

    public ScoreCalculator(CardCatalog catalog){
        this.catalog = catalog;
    }

    public int calculateCardPower(CardInstance card, PlayerState playerState, Set<Row> weather){
        throw new UnsupportedOperationException();
    }

    public int calculateRowScore(PlayerState playerState, Row row, Set<Row> weather){
        throw new UnsupportedOperationException();
    }

    public int calculatePlayerScore(PlayerState playerState, Set<Row> weather){
        throw new UnsupportedOperationException();
    }
}
