package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.QueueOrigin;

/** Maps {@link QueueOrigin} to {@code JOIN}/{@code STAFF}/{@code CARRY_OVER}. */
@Converter
public class QueueOriginConverter implements AttributeConverter<QueueOrigin, String> {

    @Override
    public String convertToDatabaseColumn(QueueOrigin attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public QueueOrigin convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QueueOrigin.fromCode(dbData);
    }
}
