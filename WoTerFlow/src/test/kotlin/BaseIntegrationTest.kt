import io.ktor.client.*
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll

open class BaseIntegrationTest {
    companion object {
        private val testDataDir = "data/tdb-test"
        lateinit var testApplication: TestApplication

        @JvmStatic
        lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun setup() = runBlocking {
            if (::testApplication.isInitialized) return@runBlocking

            testApplication = TestApplication {
                application {
                    module(testDataDir)
                }
            }
            testApplication.start()
            client = testApplication.createClient {
                install(SSE)
            }
        }
    }

    protected fun createNewTestClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
        testApplication.createClient {
            install(SSE)

            config()
        }

}