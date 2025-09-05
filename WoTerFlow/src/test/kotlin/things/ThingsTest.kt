package things

import BaseIntegrationTest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import com.google.common.io.Resources
import kotlin.test.Test
import kotlin.test.assertEquals

class ThingsTest: BaseIntegrationTest() {
    private val td = Resources.getResource("tds/td.json").readText().trim()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"

    @Test
    fun testTdCreation() = runTest {
        val putResponse = TestUtils.putThingDescription(td, thingId, client)
        assertEquals(HttpStatusCode.Companion.Created, putResponse.status)

        val getResponse = TestUtils.getThingDescription(thingId, client)
        assertEquals(HttpStatusCode.Companion.OK, getResponse.status)
        val retrievedTd = getResponse.bodyAsText().trim()
        TestUtils.assertThingDescriptionsEquals(td, retrievedTd)
    }

}