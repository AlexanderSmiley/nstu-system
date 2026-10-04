package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.JournalVisibility;

/** Maps {@link JournalVisibility} to {@code STAFF}/{@code EVERYONE}. */
@Converter
public class JournalVisibilityConverter implements AttributeConverter<JournalVisibility, String> {

    @Override
    public String convertToDatabaseColumn(JournalVisibility attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public JournalVisibility convertToEntityAttribute(String dbData) {
        return dbData == null ? null : JournalVisibility.fromCode(dbData);
    }
}
