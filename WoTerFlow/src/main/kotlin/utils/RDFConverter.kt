package utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import exceptions.ConversionException
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdOptions
import com.apicatalog.jsonld.JsonLdVersion
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.document.RdfDocument
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.readValue
import exceptions.ThingException
import jakarta.json.JsonObject
import wot.directory.DirectoryConfig.Companion.contextV10
import wot.directory.DirectoryConfig.Companion.contextV11
import java.io.StringWriter
import java.net.URI

/**
 * Utility class to perform conversions on RDF data.
 */
class RDFConverter {

    val objectMapper = ObjectMapper()

    val contextV10data: String = Utils.downloadFileAsString(contextV10)
    val contextV11data: String = Utils.downloadFileAsString(contextV11)

    var contextV10Document: JsonDocument = JsonDocument.of(contextV10data.reader())
    var contextV11Document: JsonDocument = JsonDocument.of(contextV11data.reader())

    val options11 = JsonLdOptions()

    /**
     * Initialization: loads the context documents in the JsonLdOptions.
     */
    init {
        val documentCache = mutableMapOf<String, String>()
        documentCache[contextV11] = contextV11data

        options11.documentLoader = CachedDocumentLoader(documentCache)
        //options11.expandContext = contextV11Document
        options11.base = URI(contextV11)
        options11.processingMode = JsonLdVersion.V1_1
        options11.isExplicit = false
        options11.isCompactArrays = true
        options11.isCompactToRelative = true
        options11.isProduceGeneralizedRdf = true
    }

    /**
     * Performs conversion from JSON-LD v1.0 or JSON to JSON-LD v1.1.
     *
     * @param thing The [ObjectNode] to convert to JSON-LD v1.1.
     *
     * @return The converted [ObjectNode]
     */
    fun toJsonLd11(thing: ObjectNode): ObjectNode {
        try {
            //  Check if thing has the field `securityDefinitions`.
            if (thing.has("securityDefinitions")) {
                val securityDefinitions = thing.get("securityDefinitions")
                val updatedSecurityDefinitions = JsonNodeFactory.instance.objectNode()

                securityDefinitions.fieldNames().forEach { fieldName ->
                    val securityDefinition = securityDefinitions.get(fieldName)

                    //  If it is `@none`, a new node `scheme` will be set to `nosec_sc`.
                    if (fieldName == "@none") {
                        val scheme = securityDefinition.get("scheme").textValue()
                        val newNode = JsonNodeFactory.instance.objectNode()
                        newNode.put("scheme", scheme)
                        updatedSecurityDefinitions.set("nosec_sc", newNode)
                    } else {
                        //  Else the prefix `td:` will be removed from `td:scheme`.
                        if (securityDefinition.has("td:scheme")) {
                            val scheme = securityDefinition.get("td:scheme")
                            (securityDefinition as ObjectNode).put("scheme", scheme.textValue())
                            securityDefinition.remove("td:scheme")
                        }
                        updatedSecurityDefinitions.set(fieldName, securityDefinition)
                    }
                }

                //  The field `securityDefinitions` is updated.
                thing.replace("securityDefinitions", updatedSecurityDefinitions)
            }

            //  The field `hasSecurityConfiguration` is renamed to `security`.
            if (thing.has("hasSecurityConfiguration")) {
                thing.set<ObjectNode>("security", thing.remove("hasSecurityConfiguration"))
            }

            //  The `security` becomes an ArrayNode.
            if (thing.has("security") && thing.get("security") !is ArrayNode) {
                val security = objectMapper.createArrayNode()
                security.add(thing.remove("security"))
                thing.set<ArrayNode>("security", security)
            }

            //  `context` is being updated to JSON-LD v1.1.
            when (val context = thing.get("@context")) {
                is ObjectNode -> {
                    val modifiedContext = context.deepCopy()
                    if (modifiedContext.has(contextV10)) {
                        modifiedContext.put(contextV10, contextV11)
                        thing.set<ObjectNode>("@context", modifiedContext)
                    }
                }

                is ArrayNode -> {
                    val modifiedContext = context.deepCopy()
                    val elements = modifiedContext.elements()
                    var index = 0
                    while (elements.hasNext()) {
                        val element = elements.next()
                        if (element.isTextual && element.asText() == contextV10) {
                            (modifiedContext as ArrayNode).set(index, TextNode.valueOf(contextV11))
                        }
                        index++
                    }
                    thing.set<ObjectNode>("@context", modifiedContext)

                }

                is TextNode -> {
                    if (context.asText() == contextV10) {
                        thing.put("@context", contextV11)
                    }
                }

                null -> {
                    thing.put("@context", contextV11)
                }
            }


            //  Registration infos are set.
            if (thing.has("registration")) {
                val registration = thing.get("registration") as ObjectNode
                registration.remove("id")
            }

            //  `title` field purified from prefix.
            if (thing.has("td:title"))
                thing.replace("title", thing.remove("td:title"))

            //  `version` field set to `1.1`
            thing.put("@version", "1.1")

            return thing
        } catch (e: Exception) {
            throw ConversionException("Error converting to JSON-LD 1.1")
        }
    }

    /**
     * Performs conversion from RDF [Model] to [ObjectNode].
     *
     * @param rdfModel Model to convert.
     *
     * @return The converted [ObjectNode].
     */
    fun fromRdf(rdfModel: Model): ObjectNode {
        try {
            val document = RdfDocument.of(toString(rdfModel).reader())
            val jsonArray = JsonLd.fromRdf(document).options(options11).get()
            val jsonDocument = JsonDocument.of(jsonArray.toString().reader())

            val expanded = JsonLd.expand(jsonDocument).options(options11).get()
            val expandedDocument = JsonDocument.of(expanded)

            val framed = JsonLd.frame(expandedDocument, contextV11Document).options(options11).get()
            val graphed = getGraphFromModel(framed)

            return graphed!!
        } catch (e: Exception) {
            throw ConversionException("Error converting from RDF to JSON-LD: ${e.message}")
        }
    }

    /**
     * Performs conversion from JSON-LD [String] to RDF [Model] of Language [Lang.JSONLD11].
     *
     * @param jsonLdString The [String] value of the content of a JSON-LD.
     *
     * @return The converted [Model].
     */
    fun toRdf(jsonLdString: String): Model {
        try {
            val document = JsonDocument.of(jsonLdString.reader())

            val expanded = JsonLd.expand(document).options(options11)
            val expandedDocument = expanded.get()

            val model = ModelFactory.createDefaultModel()

            expandedDocument.forEach {
                RDFDataMgr.read(model, it.toString().reader(), null, Lang.JSONLD11)
            }

            return model
        } catch (e: JsonLdError) {
            throw ThingException("${e.message}")
        } catch (e: Exception) {
            throw ThingException("The thing must contain a @context field: ${e.message}")
        }
    }

    /**
     * Retrieves the `@graph` field from a [JsonObject].
     *
     * @param jsonObject The json object to retrieve the `@graph` field from.
     *
     * @return the `@graph` as [ObjectNode].
     */
    private fun getGraphFromModel(jsonObject: JsonObject): ObjectNode? {
        val jsonString = jsonObject.toString()

        val node = objectMapper.readValue<ObjectNode>(jsonString)

        val graphArray = node["@graph"]
        if (graphArray != null && graphArray.isArray) {
            val nonAnonItem = graphArray.find { item ->
                item is ObjectNode && item.has("id") && item["id"].textValue().startsWith("urn")
            }
            if (nonAnonItem is ObjectNode) {
                return nonAnonItem
            }
        }
        return null
    }

    /**
     * Performs conversion to [String] of an RDF [Model] of Language [Lang.NTRIPLES].
     *
     * @return the [String] conversion of the [Model].
     */
    fun toString(model: Model): String {
        try {
            val writer = StringWriter()
            model.write(writer, Lang.NTRIPLES.name, contextV11)
            return writer.toString()
        } catch (e: Exception) {
            throw ConversionException("Error converting RDF to string: ${e.message}")
        }
    }

}