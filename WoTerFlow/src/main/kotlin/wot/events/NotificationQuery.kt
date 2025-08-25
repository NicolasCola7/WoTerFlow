package wot.events

import org.apache.jena.sparql.resultset.ResultsFormat

data class NotificationQuery(
    val id: Long = 0,
    val query: String,
    val resultFormat: ResultsFormat
)
