package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.Availability;

/**
 * Maps {@link Availability} to its persisted code ({@code GUEST+} etc.). The code
 * cannot be derived from the enum name, so an explicit converter is required.
 */
@Converter
public class AvailabilityConverter implements AttributeConverter<Availability, String> {

    @Override
    public String convertToDatabaseColumn(Availability attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public Availability convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Availability.fromDatabase(dbData);
    }
}
