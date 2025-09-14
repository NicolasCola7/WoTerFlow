import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableSharedFlow
//import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RIOT
import org.apache.jena.tdb2.TDB2Factory
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import utils.Utils
import wot.directory.Directory
import wot.directory.DirectoryRoutesController
import wot.events.DefaultEventNotifier
import wot.events.EventController
import wot.events.SseEvent
import wot.td.ThingDescriptionController
import wot.td.ThingDescriptionService
import java.util.concurrent.ConcurrentHashMap


fun Application.module(dataDirectory: String = "data/tdb-data") {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson()
    }

    val thingsMap: MutableMap<String, ObjectNode> = ConcurrentHashMap()

    val model: Model = ModelFactory.createDefaultModel()

    Utils.createDirectoryIfNotExists(dataDirectory)
    val rdf_db: Dataset = TDB2Factory.connectDataset(dataDirectory)

    rdf_db.begin(ReadWrite.WRITE)
    try {
        val tdbModel = rdf_db.defaultModel
        tdbModel.add(model)
        tdbModel.commit()
    } finally {
        rdf_db.end()
    }

    val createdSseFlow = MutableSharedFlow<SseEvent>()
    val updatedSseFlow = MutableSharedFlow<SseEvent>()
    val deletedSseFlow = MutableSharedFlow<SseEvent>()
    val queryNotificationSseFlow = mutableMapOf<Long, MutableSharedFlow<SseEvent>>()

    val eventController = EventController(
        createdSseFlow,
        updatedSseFlow,
        deletedSseFlow,
        queryNotificationSseFlow
    )

    val defaultEventNotifier = DefaultEventNotifier(eventController)
    val ts = ThingDescriptionService(rdf_db, thingsMap)
    val tc = ThingDescriptionController(ts, defaultEventNotifier)

    val directory = Directory(rdf_db, thingsMap, tc, eventController)

    ts.refreshJsonDb()

    val routesController = DirectoryRoutesController(directory)

    routing {
        routesController.setupRoutes(this)
    }
}

fun main(args: Array<String>) {
    try {
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        rootLogger.level = ch.qos.logback.classic.Level.INFO

        RIOT.init()

        embeddedServer(CIO, port = 8081) {
            module()
        }.start(wait = true)
    } catch (e: Exception) {
        println(e.message)
    }
}