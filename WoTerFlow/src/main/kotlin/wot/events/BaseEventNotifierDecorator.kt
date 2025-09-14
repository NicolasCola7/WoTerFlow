package wot.events

open class BaseEventNotifierDecorator: EventNotifier {

    protected val wrappee: EventNotifier
    final override val eventController: EventController

    constructor(eventNotifier: EventNotifier) {
        wrappee = eventNotifier
        eventController = wrappee.eventController
    }

    override suspend fun notify(
        eventType: EventType,
        thingId: String
    ) {
        wrappee.notify(eventType, thingId)
    }
}