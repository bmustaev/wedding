package uz.bobnoza.wedding.entity.converter;

import jakarta.persistence.AttributeConverter;

/**
 * Maps a Java enum with conventional UPPER_SNAKE_CASE constants onto the
 * lower_snake_case TEXT values used by the database (see
 * 05_orm_friendly_enums.sql, which converts native Postgres ENUM columns
 * to TEXT + CHECK for exactly this kind of mapping).
 */
public abstract class UpperSnakeEnumConverter<T extends Enum<T>> implements AttributeConverter<T, String> {

    private final Class<T> enumType;

    protected UpperSnakeEnumConverter(Class<T> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumType, dbData.toUpperCase());
    }
}
