import TestUtils.assertThingDescriptionsEquals
import TestUtils.getThingDescription
import TestUtils.putThingDescription
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.apache.jena.ext.com.google.common.io.Resources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ThingsTest: BaseIntegrationTest() {
    private val td = Resources.getResource("td.json").readText().trim()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"

    @Test
    fun testTdCreation() = runTest {
       val putResponse = putThingDescription(td, thingId, client)
        assertEquals(HttpStatusCode.Created, putResponse.status)

        val getResponse = getThingDescription(thingId, client)
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val retrievedTd = getResponse.bodyAsText().trim()
        assertThingDescriptionsEquals(td, retrievedTd)
    }

}