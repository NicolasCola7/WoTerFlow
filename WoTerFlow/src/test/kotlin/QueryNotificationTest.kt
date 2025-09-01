
import TestUtils.putThingDescription
import TestUtils.subscribeToQueryNotificationEvent
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import org.apache.jena.ext.com.google.common.io.Resources

class QueryNotificationTest : BaseIntegrationTest() {

    private val query = Resources.getResource("select_sparql_query.txt").readText()
    private val askQuery = Resources.getResource("ask_sparql_query.txt").readText()
    private val invalidQuery = Resources.getResource("invalid_sparql_query.txt").readText()
    private val noSubjectReturnQuery = Resources.getResource("no_return_subject_select_sparql_query.txt").readText()
    private val td = Resources.getResource("td.json").readText()
    private val modifiedTd =  Resources.getResource("modifiedTd.json").readText()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"
    private val queryNotificationEventType = "query_notification"
    private val thingUpdatedEventType = "thing_updated"

    companion object {
        private val EVENT_TIMEOUT = 5.seconds
        private const val SSE_SETUP_DELAY = 100L
    }

    @Test
    fun testEvent() = runTest {
        val received = CompletableDeferred<ServerSentEvent?>()

        val sseJob = launch {
            subscribeToQueryNotificationEvent(query, received, client)
        }

        delay(SSE_SETUP_DELAY)

        putThingDescription(td, thingId, client)

        try {
            val event = withTimeout(EVENT_TIMEOUT) { received.await() }

            assertTrue(
                event?.data?.contains(thingId) == true,
                "Event payload does not contains expected td id"
            )
            assertEquals(
                queryNotificationEventType,
                event.event,
                "Event type does not matches expected type."
            )
        } catch(e: TimeoutCancellationException) {
            fail("No event received" )
        } finally {
            sseJob.cancelAndJoin()
        }
    }

    @Test
    fun testDifferentClientsConcurrentEvent() = runTest {
        val secondClient = createNewTestClient()

        val received1 = CompletableDeferred<ServerSentEvent?>()
        val received2 = CompletableDeferred<ServerSentEvent?>()

        val sseJob1 = launch {
            subscribeToQueryNotificationEvent(query, received1, client)
        }

        val sseJob2 = launch {
            subscribeToQueryNotificationEvent(query, received2, secondClient)
        }

        delay(SSE_SETUP_DELAY)

        putThingDescription(td, thingId, client)

        try {
            val event1 = withTimeout(EVENT_TIMEOUT) { received1.await() }
            val event2 = withTimeout(EVENT_TIMEOUT) { received2.await() }

            assertTrue(
                event1?.data?.contains(thingId) == true,
                "Event payload does not contains expected td id"
            )
            assertEquals(
                queryNotificationEventType,
                event1.event,
                "Event type does not matches expected type."
            )

            assertTrue(
                event2?.data?.contains(thingId) == true,
                "Event payload does not contains expected td id"
            )
            assertEquals(
                queryNotificationEventType,
                event1.event,
                "Event type does not matches expected type."
            )
        } catch(e: TimeoutCancellationException) {
            fail("No event received" )
        } finally {
            sseJob1.cancelAndJoin()
            sseJob2.cancelAndJoin()
            secondClient.close()
        }
    }

    @Test
    fun testEventOnModifiedTd() = runTest {
        val received = CompletableDeferred<ServerSentEvent?>()

        val sseJob = launch {
            subscribeToQueryNotificationEvent(query, received, client)
        }

        delay(SSE_SETUP_DELAY)

        putThingDescription(modifiedTd, thingId, client)

        try {
            val event = withTimeout(EVENT_TIMEOUT) { received.await() }
            fail("Received event $event when none was expected")
        } catch(e: TimeoutCancellationException) {
           // the test is considered passed if the timeout exceeded
        } finally {
            sseJob.cancelAndJoin()
        }
    }

    @Test
    fun testWithNoSubjectReturnQuery() = runTest {
        val received = CompletableDeferred<ServerSentEvent?>()

        val sseJob = launch {
            subscribeToQueryNotificationEvent(noSubjectReturnQuery, received, client)
        }

        delay(SSE_SETUP_DELAY)

        putThingDescription(td, thingId, client)

        try {
            val event = withTimeout(EVENT_TIMEOUT) { received.await() }

            assertTrue(
                event?.data?.contains(thingId) == true,
                "Event payload does not contains expected td id"
            )
            assertEquals(
                queryNotificationEventType,
                event.event,
                "Event type does not matches expected type."
            )
        } catch(e: TimeoutCancellationException) {
            fail("No event received" )
        } finally {
            sseJob.cancelAndJoin()
        }
    }

    @Test
    fun testEventReceptionInAllEventsChannel() = runTest {
        val queryNotificationReceived = CompletableDeferred<ServerSentEvent?>()

        // list because is expected to receive 2 events: thing_updated and query_notification
        val allEventsReceived = CompletableDeferred<List<ServerSentEvent?>>()

        val queryNotificationJob = launch {
            subscribeToQueryNotificationEvent(query, queryNotificationReceived, client)
        }

        val allEventsJob = launch {
            client.sse("events") {
                val dataList: List<ServerSentEvent> = incoming
                    .map { it }
                    .take(2)
                    .toList()
                allEventsReceived.complete(dataList)
            }
        }

        delay(SSE_SETUP_DELAY)

        putThingDescription(td, thingId, client)

        try {
            val queryNotificationChannelEvent = withTimeout(EVENT_TIMEOUT) { queryNotificationReceived.await() }
            assertTrue(
                queryNotificationChannelEvent?.data?.contains(thingId) == true,
                "Event payload does not contains expected td id"
            )
            assertEquals(
                queryNotificationEventType,
                queryNotificationChannelEvent.event,
                "Event type does not matches expected type"
            )

            val allEventsChannelEvents = withTimeout(EVENT_TIMEOUT) { allEventsReceived.await() }
            assertTrue(allEventsChannelEvents.size == 2, "Expected different events number.")

            allEventsChannelEvents.forEach { event ->
                assertTrue(
                    event?.data?.contains(thingId) == true,
                    "Event payload does not contains expected td id"
                )
                assertTrue(
                    event.event == queryNotificationEventType || event.event == thingUpdatedEventType,
                    "Expected different event type. Got:  ${event.event}; Expected: $queryNotificationEventType or $thingUpdatedEventType"
                )
            }
        } catch(e: TimeoutCancellationException) {
            fail("No event received" )
        } finally {
            queryNotificationJob.cancelAndJoin()
            allEventsJob.cancelAndJoin()
        }
    }

    @Test
    fun testWithEmptyBody() = runTest {
        val exception = assertFailsWith<SSEClientException> {
            client.sse("events/query_notification", request = {
                method = HttpMethod.Post
            }) {
                fail("SSE connection should have failed")
            }
        }

        assertEquals(HttpStatusCode.BadRequest, exception.response?.status)
    }

    @Test
    fun testWithUnsupportedQuery() = runTest {
        val exception = assertFailsWith<SSEClientException> {
            client.sse("events/query_notification", request = {
                method = HttpMethod.Post
                setBody(askQuery)
            }) {
                fail("SSE connection should have failed")
            }
        }

        assertEquals(HttpStatusCode.BadRequest, exception.response?.status)
    }

    @Test
    fun testWithInvalidQuery() = runTest {
        val exception = assertFailsWith<SSEClientException> {
            client.sse("events/query_notification", request = {
                method = HttpMethod.Post
                setBody(invalidQuery)
            }) {
                fail("SSE connection should have failed")
            }
        }

        assertEquals(HttpStatusCode.InternalServerError, exception.response?.status)
    }
}
