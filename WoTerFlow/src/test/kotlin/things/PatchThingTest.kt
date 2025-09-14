package things

import BaseIntegrationTest
import TestUtils.assertThingDescriptionsEquals
import TestUtils.patchThingDescription
import TestUtils.putThingDescription
import com.google.common.io.Resources
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchThingTest: BaseIntegrationTest() {
    private val td = Resources.getResource("tds/td.json").readText().trim()
    private val modifiedTitle = "{\"title\": \"Modified Title\"}"
    private val modifiedThingId = "{\"id\": \"urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f09\"}"
    private val modifiedTd =  Resources.getResource("tds/modifiedTd.json").readText().trim()
    private val thingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f06"
    private val nonExistentThingId = "urn:uuid:0804d572-cce8-422a-bb7c-4412fcd56f08" // the last char is different

    @BeforeEach
    fun setup() {
        runBlocking {
            putThingDescription(td, thingId, client)
        }
    }

    @Test
    fun testPatchWithValidThingId() = runBlocking {
        // patch with modified td (different title)
        val patchResponse = patchThingDescription(modifiedTitle, thingId, client)
        assertEquals(HttpStatusCode.NoContent, patchResponse.status)

        val getResponse = TestUtils.getThingDescription(thingId, client)
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val retrievedTd = getResponse.bodyAsText().trim()

        assertThingDescriptionsEquals(modifiedTd, retrievedTd)
    }

    @Test
    fun testPatchWithInvalidThingId() = runBlocking {
        val patchResponse = patchThingDescription(modifiedTitle, nonExistentThingId, client)
        assertEquals(HttpStatusCode.BadRequest, patchResponse.status)
    }
    
    @Test
    fun testPatchModifiyingThingId() = runBlocking {
        val patchResponse = patchThingDescription(modifiedThingId, nonExistentThingId, client)
        assertEquals(HttpStatusCode.BadRequest, patchResponse.status)
    }
}