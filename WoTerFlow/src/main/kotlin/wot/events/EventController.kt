package wot.events

import exceptions.UnsupportedSparqlQueryException
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.coroutines.flow.MutableSharedFlow
import org.apache.jena.query.Query
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.Syntax
import org.apache.jena.sparql.core.Var
import wot.search.sparql.SparqlController
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
                      val thingDeletedSseFlow: MutableSharedFlow<SseEvent>,
                      val queryNotificationSseFlow: MutableMap<Long, MutableSharedFlow<SseEvent>>
) {
    private val pastEvents = CopyOnWriteArrayList<SseEvent>()
    private val idCounter = AtomicLong(0)
    val queries = mutableMapOf<Long, NotificationQuery>()
    private val queryIDsCounter = AtomicLong(0)

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
    suspend fun notify(eventType: EventType, eventData: String, queryId: Long = 0) {
        val event = addEvent(eventType, eventData)

        redirectEventFlow(eventType, event, queryId)
    }

    /**
     * Redirects the [SseEvent] to the corresponding stream
     *
     * @param eventType The [EventType] of interest.
     * @param event The event to emit on the stream.
     */
    private suspend fun redirectEventFlow(eventType: EventType, event: SseEvent, queryId: Long = 0) {
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
            EventType.QUERY_NOTIFICATION -> {
                queryNotificationSseFlow[queryId]!!.emit(event)
            }
        }
    }

    suspend fun addNotificationQuery(call: ApplicationCall): Long {
        var query: String? = call.receive()
        val accept = call.request.header(HttpHeaders.Accept)

        if (query.isNullOrEmpty()) {
            throw Exception("The request body is empty.")
        }

        val parsedQuery = QueryFactory.create(query, Syntax.syntaxSPARQL_11)
        parsedQuery.addSubjectVariable()
        query = parsedQuery.toString()

        val format = SparqlController.validateNotificationQueryFormat(parsedQuery, accept)
            ?: throw UnsupportedSparqlQueryException("Mime format not supported")

        val queryId = queryIDsCounter.incrementAndGet()
        val notificationQuery = NotificationQuery(
            id = queryId,
            query = query,
            resultFormat = format,
        )

        queries.put(queryId, notificationQuery)

        if(!queryNotificationSseFlow.containsKey(queryId)) {
            queryNotificationSseFlow.put(queryId, MutableSharedFlow())
        }

        return queryId
    }

    fun Query.addSubjectVariable(): Query {
        if (!this.resultVars.contains("s")) {
            this.project.add(Var.alloc("s"))
        }

        return this
    }

}