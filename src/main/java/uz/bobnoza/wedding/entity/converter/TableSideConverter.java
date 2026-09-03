package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.TableSide;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TableSideConverter extends UpperSnakeEnumConverter<TableSide> {
    public TableSideConverter() {
        super(TableSide.class);
    }
}
