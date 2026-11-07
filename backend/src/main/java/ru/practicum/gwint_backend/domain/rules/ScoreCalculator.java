package ru.practicum.gwint_backend.domain.rules;

import ru.practicum.gwint_backend.domain.model.*;


import java.util.Set;

public class ScoreCalculator {
    private final CardCatalog catalog;

    public ScoreCalculator(CardCatalog catalog){
        this.catalog = catalog;
    }

    public int calculateCardPower(CardInstance card, PlayerState playerState, Set<Row> weather){
        CardDefinition definition = catalog.get(card.cardId());
        if (definition.isHero()){
            return definition.power();
        }

        Row row = definition.row();

        int basePower;
        if (weather.contains(row)){
            basePower = 1;
        }
        else{
            basePower = definition.power();
        }

        boolean hasHorninRow = false;

        for (HornPlacement horn : playerState.horns()){
            if (horn.row() == row) {
                hasHorninRow = true;
                break;
            }
        }
         if (hasHorninRow){
             return basePower *= 2;
         }
         else{
             return  basePower;
         }

    }

    public int calculateRowScore(PlayerState playerState, Row row, Set<Row> weather){
        int rowPower = 0;

        for (CardInstance card : playerState.board().getRow(row)){
            rowPower += calculateCardPower(card, playerState, weather);
        }

        return  rowPower;

    }

    public int calculatePlayerScore(PlayerState playerState, Set<Row> weather){
        int totalPower = 0;

        for (Row row : Row.values()){
            totalPower += calculateRowScore(playerState, row, weather);
        }
        return totalPower;
    }
}
