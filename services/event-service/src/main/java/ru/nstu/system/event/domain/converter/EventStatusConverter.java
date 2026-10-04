package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.EventStatus;

/** Maps {@link EventStatus} to {@code OPEN}/{@code CLOSED}/{@code ARCHIVED}. */
@Converter
public class EventStatusConverter implements AttributeConverter<EventStatus, String> {

    @Override
    public String convertToDatabaseColumn(EventStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public EventStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EventStatus.fromCode(dbData);
    }
}
