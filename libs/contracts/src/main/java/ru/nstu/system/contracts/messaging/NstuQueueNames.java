package ru.nstu.system.contracts.messaging;

/**
 * Naming helper for per-service consumer queues.
 *
 * <p>Each consuming service declares its own durable queue and registers its
 * listeners itself; the shared library only standardises the name so that
 * operators can reason about queues without reading service code.</p>
 */
public final class NstuQueueNames {

    private NstuQueueNames() {
    }

    /**
     * @param serviceName short service name, e.g. {@code notification}
     * @return the durable queue name {@code nstu.events.<serviceName>}
     * @throws IllegalArgumentException if the name is blank
     */
    public static String forService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        return RabbitNames.CONSUMER_QUEUE_PREFIX + serviceName;
    }
}
