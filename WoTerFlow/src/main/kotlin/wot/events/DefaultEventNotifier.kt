package wot.events

class DefaultEventNotifier(override val eventController: EventController) : EventNotifier {

    override suspend fun notify(
        eventType: EventType,
        thingId: String
    ) {
        eventController.notify(eventType, "{ \n\"id\": \"${thingId}\" }")
    }
}