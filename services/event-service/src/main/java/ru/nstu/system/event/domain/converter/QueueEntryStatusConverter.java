package ru.nstu.system.event.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.nstu.system.event.domain.QueueEntryStatus;

/** Maps {@link QueueEntryStatus} to {@code WAITING}/{@code PAUSED}/{@code PASSED}. */
@Converter
public class QueueEntryStatusConverter implements AttributeConverter<QueueEntryStatus, String> {

    @Override
    public String convertToDatabaseColumn(QueueEntryStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public QueueEntryStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QueueEntryStatus.fromCode(dbData);
    }
}
