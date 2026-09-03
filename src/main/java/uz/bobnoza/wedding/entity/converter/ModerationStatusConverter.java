package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.ModerationStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ModerationStatusConverter extends UpperSnakeEnumConverter<ModerationStatus> {
    public ModerationStatusConverter() {
        super(ModerationStatus.class);
    }
}
