package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.Audience;

/** Maps {@link Audience} to {@code ME}/{@code GROUP}/{@code STAFF}. */
@Converter
public class AudienceConverter implements AttributeConverter<Audience, String> {

    @Override
    public String convertToDatabaseColumn(Audience attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public Audience convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Audience.fromDatabase(dbData);
    }
}
