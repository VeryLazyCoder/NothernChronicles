package ru.practicum.gwint_backend.domain.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public record Board(List<CardInstance> meleeRow, List<CardInstance> rangeRow, List<CardInstance> siegeRow) {

    public Board {
        meleeRow = List.copyOf(meleeRow);
        rangeRow =  List.copyOf(rangeRow);
        siegeRow =  List.copyOf(siegeRow);
    }

    public static Board empty(){
        return new Board(new ArrayList<>(),new ArrayList<>(),new ArrayList<>() );
    }

    public List<CardInstance> getRow(Row row) {
        return switch (row){
            case MELEE -> meleeRow;
            case RANGED -> rangeRow;
            case SIEGE -> siegeRow;
        };
    }

    public Board withAddedCard(CardInstance card, Row row) {
        var currentRow = getRow(row);
        var newRow = new ArrayList<CardInstance>(currentRow);
        newRow.add(card);

        return switch (row){
            case MELEE -> new Board(newRow, rangeRow, siegeRow);
            case RANGED -> new Board(meleeRow, newRow, siegeRow);
            case SIEGE -> new Board(meleeRow, rangeRow, newRow);
        };
    }

    public int totalSize() {
        return rangeRow.size() + meleeRow.size() + siegeRow.size();
    }

    public List<CardInstance> allCards() {
        return Stream.of(meleeRow, rangeRow, siegeRow)
                .flatMap(Collection::stream)
                .toList();
    }
}
