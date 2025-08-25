package wot.events

/**
 * Enum class representing different types of events.
 *
 * @property event The string representation of the event type.
 */
enum class EventType(val event: String) {
    THING_CREATED("thing_created"),
    THING_UPDATED("thing_updated"),
    THING_DELETED("thing_deleted"),
    QUERY_NOTIFICATION("query_notification");

    companion object {
        /**
         * Retrieves the [EventType] enum value based on the provided [String] representation.
         *
         * @param event The [String] representation of the event type.
         * @return The corresponding [EventType] enum value, or null if not found.
         */
        fun fromString(event: String): EventType? {
            return entries.find { it.event == event }
        }
    }
}