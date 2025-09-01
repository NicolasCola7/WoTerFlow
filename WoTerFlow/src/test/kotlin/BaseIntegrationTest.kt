import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.apache.jena.query.ReadWrite
import org.apache.jena.tdb2.TDB2Factory
import org.junit.jupiter.api.*
import java.io.File
import java.nio.file.Files
import java.util.*

abstract class BaseIntegrationTest {

    companion object {
        private lateinit var testDataDir: String
        private lateinit var testApplication: TestApplication
        @JvmStatic
        protected lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun setupBaseClass() = runBlocking {
            testDataDir = createTempTestDir()

            testApplication = TestApplication {
                application {
                    module(testDataDir)
                }
            }
            testApplication.start()
            client = testApplication.createClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = null
                    connectTimeoutMillis = 5000   // 5 seconds connect timeout
                    socketTimeoutMillis = 10000   // 10 seconds socket timeout
                }

                install(SSE)
            }

        }

        @JvmStatic
        @AfterAll
        fun teardownBaseClass() = runBlocking {
            if (::client.isInitialized) {
                client.close()
            }
            if (::testApplication.isInitialized) {
                testApplication.stop()
            }
            deleteTestDir(testDataDir)
        }

        private fun createTempTestDir(): String {
            val tempDir = Files.createTempDirectory("woterflow-test-${UUID.randomUUID()}")
            return tempDir.toString()
        }

        private fun deleteTestDir(dirPath: String) {
            val dir = File(dirPath)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }

        fun resetDatabase() {
            val rdf_db = TDB2Factory.connectDataset(testDataDir)
            rdf_db.begin(ReadWrite.WRITE)
            try {
                rdf_db.defaultModel.removeAll()
                rdf_db.commit()
            } finally {
                rdf_db.end()
                rdf_db.close()
            }
        }
    }

    protected fun createNewTestClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
        testApplication.createClient {
            install(SSE)
            install(HttpTimeout) {
                requestTimeoutMillis = null
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            config()
        }
}