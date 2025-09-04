package search
import BaseIntegrationTest
import TestUtils.deleteThingDescription
import TestUtils.getSparql
import TestUtils.postSparql
import TestUtils.putThingDescription
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.test.runTest
import org.apache.jena.ext.com.google.common.io.Resources
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class SparqlTest: BaseIntegrationTest(){

    private val td = Resources.getResource("tds/td.json").readText()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"
    private val updateQuery = Resources.getResource("queries/update_sparql_query.txt").readText() //unsupported
    private val invalidQuery = Resources.getResource("queries/invalid_sparql_query.txt").readText()
    private val validSelectQuery = Resources.getResource("queries/select_sparql_query.txt").readText()
    private val validAskQuery = Resources.getResource("queries/ask_sparql_query.txt").readText()
    private val validDescribeQuery = Resources.getResource("queries/describe_sparql_query.txt").readText()
    private val validConstructQuery = Resources.getResource("queries/construct_sparql_query.txt").readText()

    companion object {
        private val MIME_SPARQL_JSON = "application/sparql-results+json"
        private val MIME_SPARQL_XML = "application/sparql-results+xml"
        private val MIME_SPARQL_CSV = "text/csv"
        private val MIME_SPARQL_TSV = "text/tab-separated-values"
        private val MIME_SPARQL_TURTLE = "text/turtle"
    }

    @BeforeEach
    fun beforeEach() = runTest {
        putThingDescription(td, thingId, client)
    }

    @Test
    fun testHeadSparql() = runTest {
        val response = client.head("/search/sparql")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testGetSelectSparqlQuery() = runTest {
        val response = getSparql(validSelectQuery, null, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_JSON, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithNullAccept() = runTest {
        val response = postSparql(validSelectQuery, null, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_JSON, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithJsonAccept() = runTest {
        val response = postSparql(validSelectQuery, MIME_SPARQL_JSON, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_JSON, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithXmlAccept() = runTest {
        val response = postSparql(validSelectQuery, MIME_SPARQL_XML, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_XML, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithCsvAccept() = runTest {
        val response = postSparql(validSelectQuery, MIME_SPARQL_CSV, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_CSV, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithTsvAccept() = runTest {
        val response = postSparql(validSelectQuery, MIME_SPARQL_TSV, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_TSV, responseContentType)
    }

    @Test
    fun testPostSelectSparqlQueryWithTurtleAccept() = runTest {
        val response = postSparql(validSelectQuery, MIME_SPARQL_TURTLE, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"] // should be json because in select queries MIME_SPARQL_TURTLE is not supported

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_JSON, responseContentType)
    }

    @Test
    fun testPostAskSparqlQueryWithNullAccept() = runTest {
        val response = postSparql(validAskQuery, null, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains("true"))
        assertEquals(MIME_SPARQL_JSON, responseContentType)

        // deleting the td, the query should return false
        deleteThingDescription(thingId, client)
        val response2 = postSparql(validAskQuery, null, client)
        val responseBody2 = response2.bodyAsText()
        val responseContentType2 = response2.headers["Content-Type"]

        assertTrue(responseBody2.contains("false"))
        assertEquals(MIME_SPARQL_JSON, responseContentType2)
    }

    @Test
    fun testPostDescribeSparqlQueryWithNullAccept() = runTest {
        val response = postSparql(validDescribeQuery, null, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_TURTLE, responseContentType)

        // deleting the td, the query should not contain the thing id
        deleteThingDescription(thingId, client)
        val response2 = postSparql(validDescribeQuery, null, client)
        val responseBody2 = response2.bodyAsText()
        val responseContentType2 = response2.headers["Content-Type"]

        assertFalse(responseBody2.contains(thingId))
        assertEquals(MIME_SPARQL_TURTLE, responseContentType2)
    }

    @Test
    fun testPostConstructSparqlQueryWithNullAccept() = runTest {
        val response = postSparql(validConstructQuery, null, client)
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains(thingId))
        assertEquals(MIME_SPARQL_TURTLE, responseContentType)

        // deleting the td, the query should not contain the thing id
        deleteThingDescription(thingId, client)
        val response2 = postSparql(validConstructQuery, null, client)
        val responseBody2 = response2.bodyAsText()
        val responseContentType2 = response2.headers["Content-Type"]

        assertFalse(responseBody2.contains(thingId))
        assertEquals(MIME_SPARQL_TURTLE, responseContentType2)
    }

    @Test
    fun testGetSparqlWithNullQuery() = runTest {
        val response = getSparql(null, null, client)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testGetSparqlWithMissingQueryParameter() = runTest {
        val response = client.get("/search/sparql")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testPostSparqlWithMissingBodyAndQueryParameter() = runTest {
        val response = client.post("/search/sparql")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testPostSparqlWithMissingBodyButWithQueryParameter() = runTest {
        val response = client.post("/search/sparql?query=${validAskQuery.encodeURLParameter()}")
        val responseBody = response.bodyAsText()
        val responseContentType = response.headers["Content-Type"]

        assertTrue(responseBody.contains("true"))
        assertEquals(MIME_SPARQL_JSON, responseContentType)
    }

    @Test
    fun testPostInvalidSparqlQuery() = runTest {
        val response = postSparql(invalidQuery, null, client)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testPostUnsupportedSparqlQuery() = runTest {
        val response = postSparql(updateQuery, null, client)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

}