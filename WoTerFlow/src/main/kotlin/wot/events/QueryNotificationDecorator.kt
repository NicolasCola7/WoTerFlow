package wot.events

import utils.Utils
import wot.td.ThingDescriptionService

class QueryNotificationDecorator: BaseEventNotifierDecorator {

    private val thingDescriptionService: ThingDescriptionService

    constructor(eventNotifier: EventNotifier, ts: ThingDescriptionService): super(eventNotifier) {
        thingDescriptionService = ts
    }
    override suspend fun notify(
        eventType: EventType,
        thingId: String
    ) {
        super.notify(eventType, thingId)

        executeAndNotifyQueries(thingId,thingDescriptionService)
    }

    /**
     * Executes all queries and notify the corresponding SSE flow if the query's result matches the td's id
     *
     * @param thingId the id of td the query result should match
     */
    suspend fun executeAndNotifyQueries(
        thingId: String,
        ts: ThingDescriptionService
    ) {
        for (id in eventController.queries.keys) {
            val query = eventController.queries[id]
            val stringQueryResult = ts.executeNotificationQuery(query!!)
            val jsonQueryResult = Utils.toJson(stringQueryResult)

            val bindings = jsonQueryResult.get("results").get("bindings")
            if (bindings.isEmpty) {
                continue
            }

            for (td in bindings) {
                val resultTdId = td.get("s").get("value").asText()

                if (resultTdId.equals(thingId)) {
                    eventController.notify(
                        EventType.QUERY_NOTIFICATION,
                        "{ \n\"id\": \"${thingId}\" }",
                        id
                    )
                    break
                }
            }
        }
    }
}