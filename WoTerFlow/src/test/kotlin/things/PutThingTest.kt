package things

import BaseIntegrationTest
import TestUtils.putThingDescription
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import com.google.common.io.Resources
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class PutThingTest: BaseIntegrationTest() {
    private val td = Resources.getResource("tds/td.json").readText().trim()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"
    private val nonCorrespondingThingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f07" // the last char is different

    @BeforeEach
    fun setup() {
        runBlocking {
            putThingDescription(td, thingId, client)
        }
    }

    @Test
    fun testPutWithCorrespondingThingId() = runTest {
        val putResponse = putThingDescription(td, thingId, client)
        assertEquals(HttpStatusCode.NoContent, putResponse.status)

        val getResponse = TestUtils.getThingDescription(thingId, client)
        assertEquals(HttpStatusCode.Companion.OK, getResponse.status)
        val retrievedTd = getResponse.bodyAsText().trim()

        TestUtils.assertThingDescriptionsEquals(td, retrievedTd)
    }

    @Test
    fun testPutThingWithNonCorrespondingThingId() = runBlocking {
        val putResponse = putThingDescription(td, nonCorrespondingThingId, client)
        assertEquals(HttpStatusCode.BadRequest, putResponse.status)
    }
}