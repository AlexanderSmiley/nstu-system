package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.EventType;

/** Maps {@link EventType} to {@code QUEUE}. */
@Converter
public class EventTypeConverter implements AttributeConverter<EventType, String> {

    @Override
    public String convertToDatabaseColumn(EventType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public EventType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EventType.fromCode(dbData);
    }
}
