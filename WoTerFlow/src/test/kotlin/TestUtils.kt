import BaseIntegrationTest.Companion.client
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.sse.ServerSentEvent
import io.restassured.internal.path.json.JsonPrettifier
import kotlinx.coroutines.CompletableDeferred
import utils.RDFConverter
import kotlin.test.fail

object TestUtils {

    private val converter = RDFConverter()
    private val jsonMapper = ObjectMapper()

    suspend fun putThingDescription(td: String, id: String, client: HttpClient): HttpResponse {
         return client.put("things/$id") {
            header(HttpHeaders.ContentType, "application/td+json")
            setBody(td)
        }
    }

    suspend fun getThingDescription(id: String, client: HttpClient): HttpResponse {
        return client.get("things/$id")
    }

    suspend fun subscribeToQueryNotificationEvent(
        body: String,
        received: CompletableDeferred<ServerSentEvent?>,
        client: HttpClient
    ) {
        client.sse("events/query_notification", request = {
            method = HttpMethod.Post
            setBody(body)
        }) {
            incoming.collect { event ->
                if (!received.isCompleted) received.complete(event)
            }
        }
    }

    fun assertThingDescriptionsEquals(td1: String, td2: String) {
        val objectNode1 = jsonMapper.readValue<ObjectNode>(td1)
        val rdfModel1 = converter.toRdf(converter.toJsonLd11(objectNode1).toString())
        val rdfModel2 = converter.toRdf(td2)

        if(!rdfModel1.isIsomorphicWith(rdfModel2)) {
            val json2 =  JsonPrettifier.prettifyJson(td2)

            fail("Retrieved td does not matches the inserted one:\nExpected: $td1;\nGot: $json2")
        }
    }
}
