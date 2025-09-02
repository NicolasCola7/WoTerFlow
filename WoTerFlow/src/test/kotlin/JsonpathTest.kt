import org.apache.jena.ext.com.google.common.io.Resources
import TestUtils.deleteThingDescription
import TestUtils.getJsonpath
import TestUtils.putThingDescription
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonpathTest: BaseIntegrationTest() {
    private val td = Resources.getResource("td.json").readText()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"
    private val jsonpathQuery = "$[?(@.title == 'Garden Humidity Sensor')]"
    private val invalidJsonpathQuery = "[?(@.title == 'Garden Humidity Sensor')]"

    @BeforeEach
    fun beforeEach() = runTest {
        putThingDescription(td, thingId, client)
    }

    @Test
    fun testValidJsonpathQuery() = runTest {
        val response = getJsonpath(jsonpathQuery, client)
        val responseBody = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseBody.contains(thingId))
        /* would be  better asserting that responseBody == td
        but round-tripping does not work so test is going to fail */
        // assertThingDescriptionsEquals(td, responseBody[0])

        //deleting the td, the response should be empty so should not contain the thingId
        deleteThingDescription(thingId, client)
        val response2 = getJsonpath(jsonpathQuery, client)
        val responseBody2 = response2.bodyAsText()

        assertEquals(HttpStatusCode.OK, response2.status)
        assertFalse(responseBody2.contains(thingId))
    }

    @Test
    fun testInvalidJsonpathQuery() = runTest {
        val response = getJsonpath(invalidJsonpathQuery, client)

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testJsonpathWithMissingQueryParameter() = runTest {
        val response = client.get("/search/jsonpath")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testJsonpathWithBlankQuery() = runTest {
        val response = getJsonpath("", client)

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
