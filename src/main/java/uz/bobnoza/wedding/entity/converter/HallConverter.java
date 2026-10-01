package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.Hall;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HallConverter extends UpperSnakeEnumConverter<Hall> {
    public HallConverter() {
        super(Hall.class);
    }
}
