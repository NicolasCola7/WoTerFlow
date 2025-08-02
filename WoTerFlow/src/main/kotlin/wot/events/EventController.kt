package wot.events

import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Controller responsible for managing Server-Sent Event (SSE) flows for different types of events. *
 * @property thingCreatedSseFlow The flow for broadcasting "Thing Created" events.
 * @property thingUpdatedSseFlow The flow for broadcasting "Thing Updated" events.
 * @property thingDeletedSseFlow The flow for broadcasting "Thing Deleted" events.
 */
class EventController(val thingCreatedSseFlow: MutableSharedFlow<SseEvent>,
                      val thingUpdatedSseFlow: MutableSharedFlow<SseEvent>,
                      val thingDeletedSseFlow: MutableSharedFlow<SseEvent>
) {
    private val pastEvents = CopyOnWriteArrayList<SseEvent>()
    private val idCounter = AtomicLong(0)

    /**
     * Appends the new [SseEvent] to the events list that have already been created.
     *
     * @param eventType The event type to add.
     * @param eventData The event data to add.
     *
     * @return The [SseEvent] that has been added.
     */
    private fun addEvent(eventType: EventType, eventData: String): SseEvent {
        val event = SseEvent(
            data = eventData,
            event = eventType.toString().toLowerCasePreservingASCIIRules(),
            id = idCounter.getAndIncrement().toString()
        )

        pastEvents.add(event)
        return event
    }

    /**
     * Retrieves past events based on the provided id.
     *
     * @param lastReceivedId The last received ID. Events with IDs greater than this will be included.
     * @param eventTypes The types of events to include.
     *
     * @return A list of [SseEvent] representing past events matching the criteria.
     */
    fun getPastEvents(lastReceivedId: String?, vararg eventTypes: EventType): List<SseEvent> {
        //  Early return if pastEvents is empty or the lastReceivedId is null
        if (pastEvents.isEmpty() || lastReceivedId == null)
            return emptyList()

        //  Filter past events by EventType and ID
        return lastReceivedId.toLongOrNull()?.let { lastId ->
            pastEvents.filter { event ->
                (event.id?.toLong() ?: 0) > lastId &&
                    EventType.fromString(event.event.toString()) in eventTypes
            }
        } ?: emptyList()
    }

    /**
     * Notifies within the corresponding [SseEvent] stream.
     *
     * @param eventType The [EventType] of interest.
     * @param eventData The [SseEvent] data.
     */
    suspend fun notify(eventType: EventType, eventData: String) {
        val event = addEvent(eventType, eventData)

        redirectEventFlow(eventType, event)
    }

    /**
     * Redirects the [SseEvent] to the corresponding stream
     *
     * @param eventType The [EventType] of interest.
     * @param event The event to emit on the stream.
     */
    private suspend fun redirectEventFlow(eventType: EventType, event: SseEvent) {
        when (eventType) {
            EventType.THING_CREATED -> {
                thingCreatedSseFlow.emit(event)
            }
            EventType.THING_UPDATED -> {
                thingUpdatedSseFlow.emit(event)
            }
            EventType.THING_DELETED -> {
                thingDeletedSseFlow.emit(event)
            }
        }
    }
}