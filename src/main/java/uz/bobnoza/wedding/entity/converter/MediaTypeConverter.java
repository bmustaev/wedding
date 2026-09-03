package uz.bobnoza.wedding.entity.converter;

import uz.bobnoza.wedding.entity.MediaType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MediaTypeConverter extends UpperSnakeEnumConverter<MediaType> {
    public MediaTypeConverter() {
        super(MediaType.class);
    }
}
