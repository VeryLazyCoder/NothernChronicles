package ru.practicum.gwint_backend.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Board {
    private List<CardInstance> meleeRow;
    private List<CardInstance> rangeRow;
    private List<CardInstance> siegeRow;

    public Board(List<CardInstance> melee, List<CardInstance> ranged, List<CardInstance> siege) {
        meleeRow = List.copyOf(melee);
        rangeRow =  List.copyOf(ranged);
        siegeRow =  List.copyOf(siege);
    }

    public static Board Empty(){
        return new Board(new ArrayList<>(),new ArrayList<>(),new ArrayList<>() );
    }

    public List<CardInstance> getRow(Row row) {
        return switch (row){
            case MELEE -> meleeRow;
            case RANGED -> rangeRow;
            case SIEGE -> siegeRow;
        };
    }
}
