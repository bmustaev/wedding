package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.ImportStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ImportStatusConverter extends UpperSnakeEnumConverter<ImportStatus> {
    public ImportStatusConverter() {
        super(ImportStatus.class);
    }
}
