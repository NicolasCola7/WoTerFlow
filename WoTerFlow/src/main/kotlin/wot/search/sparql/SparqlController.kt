package wot.search.sparql

import exceptions.MissingQueryParameterException
import exceptions.UnsupportedSparqlQueryException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.apache.jena.query.Dataset
import org.apache.jena.query.Query
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.Syntax
import org.apache.jena.shared.NotFoundException
import org.apache.jena.sparql.resultset.ResultsFormat

/**
 * Controller responsible for managing SPARQL Semantic queries.
 */
class SparqlController {
    companion object {
        private val MIME_SPARQL_JSON = "application/sparql-results+json"
        private val MIME_SPARQL_XML = "application/sparql-results+xml"
        private val MIME_SPARQL_CSV = "text/csv"
        private val MIME_SPARQL_TSV = "text/tab-separated-values"
        private val MIME_SPARQL_TURTLE = "text/turtle"

        /**
         * Executes the SPARQL query.
         *
         * @param call The [ApplicationCall] representing the HTTP request.
         * @param db The RDF [Dataset] to operate on.
         */
        suspend fun executeSparqlQuery(call: ApplicationCall, db: Dataset) {
            try {
                val query = getQueryFromRequest(call)
                val accept = call.request.header(HttpHeaders.Accept)

                if (query == null) {
                    throw MissingQueryParameterException("Query parameter is missing")
                }

                val format = validateQueryFormat(query, accept)
                    ?: throw UnsupportedSparqlQueryException("Mime format not supported")

                val type = getResponseType(format)
                call.response.header(HttpHeaders.ContentType, type)

                call.respond(SparqlService.executeQuery(query, format, db).toString())
            } catch (e: MissingQueryParameterException) {
                call.respond(HttpStatusCode.BadRequest, "${e.message}")
            }catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Requested Thing not found")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
            }
        }

        /**
         * Validates the format of a SPARQL [query] and determines the appropriate [ResultsFormat] based on the query type.
         *
         * @param query The SPARQL query to evaluate.
         * @param mimeType The desired MIME type for the query result.
         *
         * @return The result format corresponding to the query type and MIME type.
         * @throws Exception If the query type is not supported or an invalid MIME type is provided.
         */
        private fun validateQueryFormat(query: String, mimeType: String?): ResultsFormat? {
            val parsedQuery = QueryFactory.create(query, Syntax.syntaxSPARQL_11)
            return when {
                parsedQuery.isAskType || parsedQuery.isSelectType -> {
                    when (mimeType) {
                        MIME_SPARQL_XML -> ResultsFormat.FMT_RS_XML
                        MIME_SPARQL_CSV -> ResultsFormat.FMT_RS_CSV
                        MIME_SPARQL_TSV -> ResultsFormat.FMT_RS_TSV
                        else -> ResultsFormat.FMT_RS_JSON
                    }
                }

                parsedQuery.isConstructType || parsedQuery.isDescribeType -> ResultsFormat.FMT_RDF_TURTLE
                else -> throw UnsupportedSparqlQueryException("Supported SPARQL queries are SELECT, ASK, DESCRIBE, and CONSTRUCT")
            }
        }

        /**
         * Validates the format of a SPARQL notification [query] and determines the appropriate [ResultsFormat] based on the query type.
         *
         * @param query The SPARQL query to evaluate.
         * @param mimeType The desired MIME type for the query result.
         *
         * @return The result format corresponding to the query type and MIME type.
         * @throws Exception If the query type is not supported or an invalid MIME type is provided.
         */
        fun validateNotificationQueryFormat(query: Query, mimeType: String?): ResultsFormat? {
            return when {
                query.isSelectType -> {
                    when (mimeType) {
                        MIME_SPARQL_XML -> ResultsFormat.FMT_RS_XML
                        MIME_SPARQL_CSV -> ResultsFormat.FMT_RS_CSV
                        MIME_SPARQL_TSV -> ResultsFormat.FMT_RS_TSV
                        else -> ResultsFormat.FMT_RS_JSON
                    }
                }

                else -> throw UnsupportedSparqlQueryException("Only SELECT Sparql queries are supported for Query Notifications")
            }
        }

        /**
         * Determines the appropriate [ResultsFormat] MIME type.
         *
         * @param format The [ResultsFormat] to check.
         *
         * @return The MIME type.
         */
        fun getResponseType(format: ResultsFormat): String {
            return when (format) {
                ResultsFormat.FMT_RS_JSON -> MIME_SPARQL_JSON
                ResultsFormat.FMT_RS_XML -> MIME_SPARQL_XML
                ResultsFormat.FMT_RS_CSV -> MIME_SPARQL_CSV
                ResultsFormat.FMT_RS_TSV -> MIME_SPARQL_TSV
                ResultsFormat.FMT_RDF_TURTLE -> MIME_SPARQL_TURTLE
                else -> MIME_SPARQL_JSON
            }
        }

        /**
         * Extracts the SPARQL query from a given [ApplicationCall.request] based on the [HttpMethod].
         *
         * @param call Application call to extract from.
         *
         * @return query as [String] if exists, otherwise `null`.
         */
        private suspend fun getQueryFromRequest(call: ApplicationCall): String? {
            return when (call.request.httpMethod) {
                HttpMethod.Get -> call.request.queryParameters["query"]
                HttpMethod.Post -> {
                    val body = call.receiveText()
                    body.ifEmpty {
                        call.request.queryParameters["query"]
                    }
                }

                else -> null
            }
        }
    }
}