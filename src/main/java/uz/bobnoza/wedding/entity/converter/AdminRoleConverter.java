package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.AdminRole;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AdminRoleConverter extends UpperSnakeEnumConverter<AdminRole> {
    public AdminRoleConverter() {
        super(AdminRole.class);
    }
}
