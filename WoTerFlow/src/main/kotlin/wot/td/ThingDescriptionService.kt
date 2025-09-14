package wot.td

import com.fasterxml.jackson.databind.node.ObjectNode
import errors.ValidationError
import exceptions.ConversionException
import exceptions.ThingException
import exceptions.ValidationException
import io.ktor.server.plugins.BadRequestException
import org.apache.jena.query.Dataset
import org.apache.jena.query.TxnType
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import utils.RDFConverter
import utils.Utils
import wot.directory.DirectoryConfig
import wot.events.NotificationQuery
import wot.search.sparql.SparqlService
import java.time.Instant
import java.util.UUID

/**
 * Service responsible for managing [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TDs) within the system.
 *
 * @param dbRdf The RDF dataset used for querying and storing [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td).
 * @param thingsMap A mutable map containing Thing IDs as keys and corresponding Thing Descriptions as values.
 */
class ThingDescriptionService(dbRdf: Dataset, private val thingsMap: MutableMap<String, ObjectNode>) {
    private val rdfDataset: Dataset = dbRdf

    private var ttlContextModel: Model = Utils.loadModel(DirectoryConfig.ttlContextLocal, Lang.TURTLE)
    private var xmlShapesModel: Model = Utils.loadModel(DirectoryConfig.xmlShapesLocal, Lang.RDFXML)

    private val converter = RDFConverter()

    /**
     * Refreshes the `JSON` representation of a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) in the in-memory cache.
     *
     * @param graphId The [UUID] of the Thing Description in the RDF [Dataset].
     */
    private fun refreshJsonDbItem(graphId: String) {
        try {
            val ttlModel = Utils.loadRDFModelById(rdfDataset, graphId)

            if (!ttlModel.isEmpty){
                val objNode = converter.fromRdf(ttlModel)
                val thing = converter.toJsonLd11(Utils.toJson(objNode.toString()))

                //  Update the in-memory cache with the refreshed JSON representation
                thingsMap[graphId] = thing
            } else {
                thingsMap.remove(graphId)
            }
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb item with id: $graphId: ${e.message}")
        }
    }

    /**
     * Refreshes the `JSON` representations of all the [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TDs) in the in-memory cache.
     **/
    fun refreshJsonDb() {
        rdfDataset.begin(TxnType.READ)

        try {
            val ttlList = Utils.loadRDFDatasetIntoModelList(rdfDataset)
            val things = ttlList.map { converter.toJsonLd11(converter.fromRdf(it)) }

            //  Clear the things map and populate it back with the updated dataset
            thingsMap.clear()

            //  Update the in-memory cache with the refreshed JSON representation
            things.forEach { thing ->
                thing["id"]?.asText()?.let { id ->
                    thingsMap[Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)] = thing
                }
            }

            Utils.printWatermark()
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    /**
     * Inserts an Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the generated [UUID] along with the TD.
     *
     * This function takes an anonymous TD represented as a JSON object, inserts it into the RDF [Dataset],
     * and generates a unique ID for the TD. The generated ID is returned along with the updated TD containing
     * the assigned ID.
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     *
     * @return A [Pair] containing the generated [UUID] and the updated TD with the assigned [UUID].
     * @throws Exception If an error occurs during the operation, the transaction is aborted, and the error is propagated.
     */
    fun insertAnonymousThing(td: ObjectNode): Pair<String, ObjectNode> {
        var mapId: String = ""
        synchronized(this) {
            return runCatching {
                rdfDataset.createWithTransaction {
                    val pair = generateUniqueID()
                    val id = pair.first
                    val graphId = pair.second

                    td.put("@id", id)

                    //  Checking the jsonld version and upgrading if needed
                    val tdVersion11p = Utils.isJsonLd11OrGreater(td)
                    val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

                    //  JsonLd decoration with missing fields
                    decorateThingDescription(tdV11)

                    //  Model Validation
                    val jsonRdfModel = converter.toRdf(tdV11.toString())
                    validateThingDescription(jsonRdfModel)

                    //  Query preparation for RDF data storing
                    val rdfTriplesString = converter.toString(jsonRdfModel)
                    val query = """
                        INSERT DATA {
                            GRAPH <$graphId> {
                                $rdfTriplesString
                            }
                        }
                    """.trimIndent()

                    // Execute query
                    SparqlService.update(query, rdfDataset)

                    mapId = graphId
                    Pair(id, tdV11)
                }
            }.onSuccess {
                refreshJsonDbItemIfNotEmpty(mapId)
            }.getOrElse { e ->
                throw handleException(e, "Insert Anonymous Thing")
            }
        }
    }

    /**
     * Creates or Updates (if already existing) a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the updated [UUID] along with the TD.
     *
     * This function takes a TD represented as a JSON object, inserts or updates it into the RDF [Dataset].
     * The [UUID] is returned along with the updated TD containing the assigned [UUID].
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     * @param id the thing id inserted in the url
     *
     * @return A [Pair] containing the generated [UUID] and the updated TD with the assigned [UUID].
     * @throws Exception If an error occurs during the operation, the transaction is aborted, and the error is propagated.
     */
    fun updateThing(td: ObjectNode, id: String): Pair<String, Boolean> {
        val tdBodyId: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        if(id != tdBodyId) throw ThingException("Id in the url does not corresponds to the id in the request's body")

        synchronized(this) {
            val existsAlready = checkIfThingExists(id)

            return runCatching {
                rdfDataset.updateWithTransaction {
                    //  Checking the jsonld version and upgrading if needed
                    val tdVersion11p = Utils.isJsonLd11OrGreater(td)
                    val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

                    //  JsonLd decoration with missing fields
                    decorateThingDescription(tdV11)

                    //  Model Validation
                    val jsonRdfModel = converter.toRdf(tdV11.toString())
                    validateThingDescription(jsonRdfModel)

                    // Query preparation for RDF data storing
                    val rdfTriplesString = converter.toString(jsonRdfModel)
                    val query = """
                        DELETE WHERE {
                            GRAPH <$graphId> {
                                ?s ?p ?o
                            }
                        };
                        INSERT DATA {
                            GRAPH <$graphId> {
                                $rdfTriplesString
                            }
                        }
                    """.trimIndent()

                    // Execute query
                    SparqlService.update(query, rdfDataset)

                    Pair(id, existsAlready)
                }
            }.onSuccess {
                refreshJsonDbItemIfNotEmpty(graphId)
            }.getOrElse { e ->
                throw handleException(e, "Update Thing")
            }
        }
    }

    /**
     * Partially Updates a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the updated [UUID].
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     * @param id the thing id inserted in the url
     *
     * @return A [String] containing the [UUID] of the updated TD.
     * @throws Exception If an error occurs during the operation, the transaction is aborted, and the error is propagated.
     */
    fun patchThing(td: ObjectNode, id: String): String {
        val tdBodyId = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()

        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        if(tdBodyId != null) throw ThingException("You can not modify the thing id")

        synchronized(this) {
            return runCatching {
                rdfDataset.patchWithTransaction {
                    val thing = retrieveThingById(id)
                        ?: throw ThingException("Thing with id: $graphId does not exist.")

                    val thingCopy = thing.deepCopy().setAll<ObjectNode>(td)
                    removeEmptyProperties(thingCopy)
                    decorateThingDescription(thingCopy)

                    //  Model Validation
                    val jsonRdfModel = converter.toRdf(thingCopy.toString())
                    validateThingDescription(jsonRdfModel)

                    thing.setAll<ObjectNode>(td)

                    // Query preparation for RDF data storing
                    val rdfTriplesString = converter.toString(jsonRdfModel)
                    val query = """
                        DELETE WHERE {
                            GRAPH <$graphId> {
                                ?s ?p ?o
                            }
                        };
                        INSERT DATA {
                            GRAPH <$graphId> {
                                $rdfTriplesString
                            }
                        }
                    """.trimIndent()

                    // Execute query
                    SparqlService.update(query, rdfDataset)

                    id
                }
            }.onSuccess {
                refreshJsonDbItemIfNotEmpty(graphId)
            }.getOrElse { e ->
                throw handleException(e, "Patch Thing")
            }
        }
    }

    /**
     * Deletes a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) from the RDF [Dataset] based on the specified [UUID].
     *
     * @param id The ID of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be deleted.
     *
     * @throws Exception If an error occurs during the operation, the transaction is aborted, and the error is propagated.
     */
    fun deleteThingById(id: String) {
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
        synchronized(this) {
            runCatching {
                rdfDataset.deleteWithTransaction {
                    val deleteQuery = "DELETE WHERE { GRAPH <$graphId> { ?s ?p ?o } }"
                    SparqlService.update(deleteQuery, rdfDataset)
                }
            }.onSuccess {
                refreshJsonDbItemIfNotEmpty(graphId)
            }.getOrElse { e ->
                throw handleException(e, "Delete Thing")
            }
        }
    }

    /**
     * Retrieves a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) from the in-memory cache based on the specified [UUID].
     *
     * @param id The [UUID] of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be retrieved.
     *
     * @return The retrieved [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) as an [ObjectNode], or null if the [UUID] is not found.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun retrieveThingById(id: String): ObjectNode? {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap[graphId]
        } catch (e: Exception){
            throw ThingException("Retrieve Get: ${e.message}")
        }
    }

    /**
     * Checks if a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) with the specified [UUID] exists in the in-memory cache.
     *
     * @param id The [UUID] of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be checked for existence.
     *
     * @return `true` if the Thing exists, `false` otherwise.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun checkIfThingExists(id: String): Boolean {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap.containsKey(graphId)
        } catch (e: Exception){
            throw ThingException("Retrieve Head: ${e.message}")
        }
    }

    /**
     * Retrieves a list of all Things from the in-memory cache.
     *
     * This function retrieves all Things stored in the in-memory cache and returns them as a list of ObjectNodes.
     *
     * @return A list of ObjectNodes representing all Things in the in-memory cache.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun retrieveAllThings(): List<ObjectNode> {
        try {
            return thingsMap.values.toList()
        } catch (e: Exception){
            throw ThingException("Retrieve All: ${e.message}")
        }
    }

    /**
     * Decorates a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) with additional information.
     *
     * @param td The original [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be decorated.
     */
    private fun decorateThingDescription(td: ObjectNode) {
        if (!td.has("@type")) {
            val typeArray = td.putArray("@type")
            typeArray.add("Thing")
        } else if (!Utils.hasThingType(td)) {
            val typeArray = td.withArray("@type")
            typeArray.add("Thing")
        }

        //  Add registration info: "created" if not already existing
        val registrationInfo: ObjectNode = if (!td.has("registration")) {
            Utils.jsonMapper.createObjectNode().put("created", Instant.now().toString())
        } else {
            td["registration"] as ObjectNode
        }

        //  Add registration info: "modified" with the current timestamp
        registrationInfo.put("modified", Instant.now().toString())
        td.set<ObjectNode>("registration", registrationInfo)
    }

    /**
     * Removes empty properties from an [ObjectNode].
     *
     * @param objectNode The ObjectNode to remove empty properties from.
     */
    private fun removeEmptyProperties(objectNode: ObjectNode) {
        val fieldsToRemove = mutableListOf<String>()

        objectNode.fieldNames().forEach { fieldName ->
            val fieldValue = objectNode.get(fieldName)
            if (fieldValue.isNull) {
                fieldsToRemove.add(fieldName)
            } else if (fieldValue.isObject) {
                removeEmptyProperties(fieldValue as ObjectNode) //  Recursive call for nested objects
            }
        }

        fieldsToRemove.forEach {
            objectNode.remove(it)
        }
    }

    /**
     * Generates a unique ID and corresponding graph ID for a Thing Description.
     *
     * @return A [Pair] containing the generated ID and its corresponding graph ID.
     */
    private fun generateUniqueID(): Pair<String, String> {
        var uuid: String
        var id: String
        var graphId: String

        do {
            uuid = UUID.randomUUID().toString()
            id = Utils.strconcat("urn:uuid:", uuid)
            graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
        } while (Utils.idExists(thingsMap.keys, graphId))

        return Pair(id, graphId)
    }

    /**
     * Performs the Syntactic and Semantic validations on an RDF [Model]
     *
     * @param jsonRdfModel The RDF [Model] representing the Thing Description in JSON-LD format.
     *
     * @throws ValidationException If syntactic or semantic validation fails, containing a list of validation errors.
     */
    private fun validateThingDescription(jsonRdfModel: Model) {
        //  Performing Syntactic Validation
        val syntacticValidationFailures =
            ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

        if (syntacticValidationFailures.isNotEmpty()) {
            val validationErrors = syntacticValidationFailures.map {
                ValidationError(
                    "Syntactic Validation",
                    it
                )
            }
            throw ValidationException(validationErrors, "Syntactic Validation Failed")
        }

        //  Performing Semantic Validation
        val semanticValidationFailures =
            ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

        if (semanticValidationFailures.isNotEmpty()) {
            val validationErrors = semanticValidationFailures.map {
                ValidationError(
                    "Semantic Validation",
                    it
                )
            }
            throw ValidationException(validationErrors, "Semantic Validation Failed")
        }
    }

    private fun refreshJsonDbItemIfNotEmpty(graphId: String){
        graphId.takeIf { it.isNotEmpty() }?.let {
            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(it)
            rdfDataset.end()
        }
    }

    /**
     * Extension function to [Dataset] class. Executes the specified action within a write transaction on the dataset, ensuring proper transaction management.
     *
     * @param action The action to be executed within the transaction, returning a [Pair] of [String] and [ObjectNode].
     *
     * @return A [Pair] representing the result of the action and the created graph's ID.
     * @throws Exception If an error occurs during the execution of the action, the transaction is aborted, and the error is propagated.
     */
    private inline fun Dataset.createWithTransaction(action: () -> Pair<String, ObjectNode>) : Pair<String, ObjectNode> {
        this.begin(TxnType.WRITE)
        return try {
            val result = action()
            this.commit()
            result
        } catch (e: Exception) {
            this.abort()
            throw e
        } finally {
            this.end()
        }
    }

    /**
     * Extension function to [Dataset] class. Executes the specified action within a write transaction on the dataset, ensuring proper transaction management.
     *
     * @param action The action to be executed within the transaction, returning a [Pair] of [String] and [Boolean].
     *
     * @return A [Pair] representing the result of the action and a [Boolean] indicating whether the resource existed before the update.
     * @throws Exception If an error occurs during the execution of the action, the transaction is aborted, and the error is propagated.
     */
    private inline fun Dataset.updateWithTransaction(action: () -> Pair<String, Boolean>) : Pair<String, Boolean> {
        this.begin(TxnType.WRITE)
        return try {
            val result = action()
            this.commit()
            result
        } catch (e: Exception) {
            this.abort()
            throw e
        } finally {
            this.end()
        }
    }

    /**
     * Extension function to [Dataset] class. Executes the specified action within a write transaction on the dataset, ensuring proper transaction management.
     *
     * @param action The action to be executed within the transaction, returning the ID of the patched resource.
     *
     * @return The ID of the patched resource.
     * @throws Exception If an error occurs during the execution of the action, the transaction is aborted, and the error is propagated.
     */

    private inline fun Dataset.patchWithTransaction(action: () -> String) : String {
        this.begin(TxnType.WRITE)
        return try {
            val result = action()
            this.commit()
            result
        } catch (e: Exception) {
            this.abort()
            throw e
        } finally {
            this.end()
        }
    }

    /**
     * Extension function to [Dataset] class. Executes the specified action within a write transaction on the dataset, ensuring proper transaction management.
     *
     * @param action The action to be executed within the transaction.
     *
     * @throws Exception If an error occurs during the execution of the action, the transaction is aborted, and the error is propagated.
     */
    private inline fun Dataset.deleteWithTransaction(action: () -> Unit) {
        this.begin(TxnType.WRITE)
        try {
            action()
            this.commit()
        } catch (e: Exception) {
            this.abort()
            throw e
        } finally {
            this.end()
        }
    }

    /**
     * Executes a notification query
     *
     * @param query [NotificationQuery] the query to execute
     */
    fun executeNotificationQuery(query: NotificationQuery): String {
        val queryResult = SparqlService.executeQuery(query.query, query.resultFormat, rdfDataset)
        return queryResult.toString()
    }

    /**
     * Handles exceptions by throwing a typed exception based on the given [Throwable] type.
     *
     * @param e The exception to be handled.
     * @param opMessage A message indicating the operation being performed when the exception occurred.
     *
     * @throws T A typed exception based on the [Throwable] type.
     * @throws BadRequestException If the request is invalid.
     * @throws ThingException If an error occurs during the insertion process.
     * @throws ValidationException If an error occurs during the validation process.
     * @throws ConversionException If an error occurs during the conversion process
     */
    private inline fun <reified T: Exception> handleException(e: Throwable, opMessage: String): T {
        when (e) {
            is BadRequestException -> throw BadRequestException("Invalid or missing @id field in the JSON body.")
            is ThingException -> throw ThingException("An error occurred while performing the $opMessage operation: ${e.message}")
            is ValidationException -> throw ValidationException(e.errors, e.message)
            is ConversionException -> throw ConversionException("An error occurred while performing the $opMessage operation: ${e.message}")
            else -> throw  ThingException("An error occurred while performing the $opMessage operation: ${e.message}.")
        }
    }
}