package utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import exceptions.ThingException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utilities class
 */
class Utils {
    companion object {
        val jsonMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Prints the project watermark.
         */
        fun printWatermark(){
                    println("""
>>
║║     _    _       _____           ______  _                  
║║    | |  | |     |_   _|          |  ___|| |                 
║║    | |  | |  ___  | |  ___  _ __ | |_   | |  ___ __      __ 
║║    | |/\| | /   \ | | / _ \| '__||  _|  | | /   \\ \ /\ / / 
║║    \  /\  /|  0  || ||  __/| |   | |    | ||  0  |\ V  V /  
║║     \/  \/  \___/ \_/ \___||_|   \_|    |_| \___/  \_/\_/   
║║                                                           v0.1.9
>>                                    github.com/dvgniele/woterflow                       
      
            """.trimIndent())
        }

        /**
         * Downloads a file from [String] containing an [Url].
         *
         * @param uri The [Url] to download as [String].
         *
         * @return The content of the download as [String].
         */
        fun downloadFileAsString(uri: String): String {
            val url = URI.create(uri).toURL()
            val connection = url.openConnection()
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0")
            val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
            val content = StringBuilder()
            var line: String?

            reader.use { it ->
                while (it.readLine().also { line = it } != null) {
                    content.append(line).append("\n")
                }
            }

            return content.toString()
        }

        /**
         * Reads the content of a file and returns it as [InputStream]?.
         *
         * @param filePath The filepath [String] to the file to read.
         *
         * @return An [InputStream] containing the content of the file, or null if an exception occurs.
         */
        fun readFileAsStream(filePath: String): InputStream? {
            return try {
                File(filePath).inputStream()
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Creates a new [Model] from the [String] content, of language [Lang].
         *
         * @param content The content of the [Model].
         * @param lang The [Lang] of the [Model].
         *
         * @return The new [Model].
         * @throws ThingException if it is not possible to load the [Model]
         */
        fun loadModel(content: String, lang: Lang): Model {
            try {
                val model = ModelFactory.createDefaultModel()
                RDFDataMgr.read(model, content.reader(), null, lang)

                return model
            } catch (e: Exception) {
                throw ThingException("Cannot load model: ${e.message}")
            }
        }

        /**
         * Creates a new [Model] from a filepath, of language [Lang].
         *
         * @param filePath The content of the [Model].
         * @param lang The [Lang] of the [Model].
         *
         * @return The new [Model].
         * @throws ThingException if it's not possible to load the [Model]
         */
        fun loadModelFromPath(filePath: String, lang: Lang): Model {
            try {
                val inputStream = readFileAsStream(filePath)

                if (inputStream != null) {
                    val model = ModelFactory.createDefaultModel()
                    RDFDataMgr.read(model, inputStream, lang)

                    return model
                } else {
                    throw ThingException("Resource content is null for path: $filePath")
                }
            } catch (e: Exception) {
                throw ThingException("Cannot load model: ${e.message} path: $filePath")
            }
        }

        /**
         * Loads the RDF [Dataset] into a [ArrayList] of [Model].
         *
         * @param dataset The [Dataset] to load into the [ArrayList] of [Model].
         *
         * @return The [ArrayList] containing the [Dataset]
         */
        fun loadRDFDatasetIntoModelList(dataset: Dataset): ArrayList<Model> {
            val modelList = ArrayList<Model>()

            dataset.listNames().forEach { graphName ->
                val model = dataset.getNamedModel(graphName)
                modelList.add(model)
            }

            return modelList
        }

        /**
         * Looks-up a [Model] by its GraphName from a [Dataset].
         *
         * @param dataset The dataset to load the [Model] from.
         * @param graphId The model id.
         *
         * @return The [Model]
         */
        fun loadRDFModelById(dataset: Dataset, graphId: String): Model {
            return dataset.getNamedModel(graphId)
        }

        /**
         * Checks if a given [ObjectNode] has the `@type` field.
         *
         * @param thing The [ObjectNode] to check.
         *
         * @return `true` if the [ObjectNode] has the `@type` field, otherwise `false`.
         */
        fun hasThingType(thing: ObjectNode): Boolean {
            val type = thing["@type"]

            if (type is JsonNode) {
                val typeValue = type.asText()
                return typeValue == "Thing" || typeValue == "https://www.w3.org/2019/wot/td#Thing"
            } else if (type is ArrayNode) {
                val uniqueTypes = type.map { it.asText() }.toSet()
                return uniqueTypes.any { it == "Thing" || it == "https://www.w3.org/2019/wot/td#Thing" }
            }

            return false
        }

        /**
         * Concatenates multiple strings value.
         *
         * @param values The strings to concatenate.
         *
         * @return The concatenation.
         */
        fun strconcat(vararg values: String): String {
            return values.joinToString("")
        }

        /**
         * Checks if the [id] is [isNullOrEmpty]
         *
         * @param id The [String] to check.
         *
         * @return The [id] if it is not null or empty.
         * @throws ThingException if the [id] is [isNullOrEmpty]
         */
        fun hasValidId(id: String?): String {
            if (id.isNullOrEmpty()) {
                throw ThingException("Thing ID not valid.")
            }
            return id
        }

        /**
         * Checks if the [HttpHeaders.ContentType] contains at least one of `application/td+json` `application/ld+json` `application/merge-patch+json`.
         *
         * @param contentTypeHeader The [HttpHeaders.ContentType] to check.
         *
         * @return `true` if It contains at least one of the types, otherwise `false`.
         */
        fun hasJsonContent(contentTypeHeader: String?): Boolean {
            if (contentTypeHeader == null)
                return false

            val contentType = ContentType.parse(contentTypeHeader)
            val allowedContentTypes = listOf(
                ContentType.Application.Json,
                ContentType.parse("application/td+json"),
                ContentType.parse("application/ld+json"),
                ContentType.parse("application/merge-patch+json")
            )

            return allowedContentTypes.any { it.match(contentType) }
        }

        /**
         * Parses the provided JSON [body] and returns it as an [ObjectNode].
         *
         * @param body The JSON [String] to parse.
         *
         * @return The parsed JSON as [ObjectNode]
         * @throws ThingException if the [body] is empty or null
         */
        fun hasBody(body: String?): ObjectNode? {
            if (body.isNullOrEmpty()) {
                throw ThingException("The request body is empty.")
            }

            return jsonMapper.readValue(body)
        }

        /**
         * Checks whether the provided [id] exists within the given [map].
         *
         * @param map The [Map] to check for the existence of [id].
         * @param id The identifier to search for in the [map].
         *
         * @return `true` if the [id] exists in the [map], `false` otherwise.
         */
        fun idExists(map: MutableSet<String>, id: String): Boolean {
            return map.contains(id)
        }

        /**
         * Checks whether the provided [targetContext] exists within the given [contexts].
         *
         * @param contexts The [ArrayNode] to check for the presence of [targetContext].
         * @param targetContext The target context to search for in the [contexts].
         *
         * @return `true` if the [targetContext] exists in the [contexts], `false` otherwise.
         */
        fun contextsContains(contexts: ArrayNode, targetContext: String): Boolean {
            return contexts.any { it.isTextual && it.asText() == targetContext }
        }

        /**
         * Check if the given [ObjectNode] contains the `@version` tag.
         *
         * @param thing The [ObjectNode] to check.
         *
         * @return `true` if [thing] contains the `@version` field, `false` otherwise.
         */
        fun isJsonLd11OrGreater(thing: ObjectNode): Boolean {
            val versionNode = thing["@version"]
            return versionNode != null
        }

        /**
         * Performs the conversion of a [String] to [ObjectNode].
         *
         * @param td The json [String] to convert.
         *
         * @return The converted [ObjectNode].
         */
        fun toJson(str: String): ObjectNode {
            val objectMapper = ObjectMapper()
            return objectMapper.readValue<ObjectNode>(str)
        }

        /**
         * Checks if the [call] should be rejected. If so, it responds
         *
         * @param call The [ApplicationCall] representing the HTTP request.
         *
         * @return `true` if the [call] has been rejected, `false` otherwise.
         */
        suspend fun rejectedDiff(call: ApplicationCall): Boolean {
            if (call.parameters.contains("diff")) {
                call.respondText(
                    text = "Request parameter 'diff' is not supported on this route.",
                    status = HttpStatusCode.NotImplemented
                )
                return true
            }
            return false
        }

        /**
         * Checks if a given [directoryPath] already exists. If not, it creates it.
         *
         * @param directoryPath The path to check (and generate).
         */
        fun createDirectoryIfNotExists(directoryPath: String) {
            val path: Path = Paths.get(directoryPath)

            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path)
                    println("Directory created: $directoryPath")
                } catch (e: Exception) {
                    println("Error creating directory: $directoryPath")
                    e.printStackTrace()
                }
            }
        }
    }
}