package dev.codemap.core.model;

/**
 * What kind of entry point an {@link EntryPoint} is.
 *
 * <p>Kind drives the icon and grouping in the report; detection rules
 * (spec §4.1) each target exactly one of these.
 */
public enum EntryPointKind {

    /** An HTTP route, whether declared by annotation or registered programmatically. */
    REST,

    /** A scheduled or cron-triggered unit of work. */
    JOB,

    /** A consumer of an asynchronous message: Kafka, RabbitMQ, JMS. */
    MESSAGE,

    /** A WebSocket or STOMP endpoint. */
    SOCKET,

    /** A UI view reachable by navigation, e.g. a Vaadin {@code @Route}. */
    UI,

    /** A process entry point: {@code public static void main}, or a runner callback. */
    BOOTSTRAP
}
