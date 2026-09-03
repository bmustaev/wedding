package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.AdminSide;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AdminSideConverter extends UpperSnakeEnumConverter<AdminSide> {
    public AdminSideConverter() {
        super(AdminSide.class);
    }
}
