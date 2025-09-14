import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
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

    suspend fun patchThingDescription(td: String, id: String, client: HttpClient): HttpResponse {
        return client.patch("things/$id") {
            header(HttpHeaders.ContentType, "application/td+json")
            setBody(td)
        }
    }

    suspend fun deleteThingDescription(id: String, client: HttpClient): HttpResponse {
        return client.delete("things/$id")
    }

    suspend fun getThingDescription(id: String, client: HttpClient): HttpResponse {
        return client.get("things/$id")
    }

    suspend fun subscribeToQueryNotificationEvent(
        body: String,
        received: CompletableDeferred<ServerSentEvent?>,
        client: HttpClient
    ) {
        return client.sse("events/query_notification", request = {
            method = HttpMethod.Post
            setBody(body)
        }) {
            incoming.collect { event ->
                if (!received.isCompleted) received.complete(event)
            }
        }
    }

    suspend fun postSparql(
        body: String?,
        accept: String?,
        client: HttpClient
    ): HttpResponse {
        return client.post("/search/sparql") {
            header("Accept", accept)
            setBody(body)
        }
    }

    suspend fun getSparql(
        query: String?,
        accept: String?,
        client: HttpClient
    ): HttpResponse {
        return client.get("/search/sparql/?query=${query?.encodeURLParameter()}") {
            header("Accept", accept)
        }
    }

    suspend fun getJsonpath(query: String?, client: HttpClient): HttpResponse {
        return client.get("/search/jsonpath?query=$query")
    }

    suspend fun getXpath(query: String?, client: HttpClient): HttpResponse {
        return client.get("/search/xpath?query=$query")
    }

    fun assertThingDescriptionsEquals(td1: String, td2: String) {
        val objectNode1 = jsonMapper.readValue<ObjectNode>(td1)
        val objectNode2 = jsonMapper.readValue<ObjectNode>(td2)
        objectNode2.remove("registration") // removes registration infos

        val rdfModel1 = converter.toRdf(converter.toJsonLd11(objectNode1).toString())
        val rdfModel2 = converter.toRdf(converter.toJsonLd11(objectNode2).toString())

        if(!rdfModel1.isIsomorphicWith(rdfModel2)) {
            var json1 = jsonMapper.writeValueAsString(objectNode1)
            json1 =  JsonPrettifier.prettifyJson(json1)

            var json2 = jsonMapper.writeValueAsString(objectNode2)
            json2 =  JsonPrettifier.prettifyJson(json2)

            fail("Retrieved td does not matches the inserted one:\nExpected: $json1;\nGot: $json2")
        }
    }


}
