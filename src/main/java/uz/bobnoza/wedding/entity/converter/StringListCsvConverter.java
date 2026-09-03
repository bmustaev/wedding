package uz.bobnoza.wedding.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps a List<String> onto a single comma-separated TEXT column.
 *
 * MariaDB has no array column type (unlike the PostgreSQL text[] this field
 * was originally mapped to), so group member names are stored as a simple
 * CSV string here instead. Not auto-applied — only guests.groupMembers uses
 * this, so it's attached explicitly with @Convert.
 */
@Converter
public class StringListCsvConverter implements AttributeConverter<List<String>, String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream()
                .map(s -> s.replace(DELIMITER, " ")) // guard against embedded commas breaking the split
                .collect(Collectors.joining(DELIMITER));
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split(DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
