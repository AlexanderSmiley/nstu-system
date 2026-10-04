package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.EntryUnit;

/** Maps {@link EntryUnit} to the persisted {@code BRIGADE}/{@code PERSON} code. */
@Converter
public class EntryUnitConverter implements AttributeConverter<EntryUnit, String> {

    @Override
    public String convertToDatabaseColumn(EntryUnit attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public EntryUnit convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EntryUnit.fromCode(dbData);
    }
}
