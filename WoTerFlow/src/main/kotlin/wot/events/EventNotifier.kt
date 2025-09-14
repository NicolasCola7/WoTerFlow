package wot.events

interface EventNotifier {

    val eventController: EventController

    suspend fun notify(eventType: EventType, thingId: String)
}