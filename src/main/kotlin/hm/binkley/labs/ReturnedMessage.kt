package hm.binkley.labs

/**
 * Subscribe to [ReturnedMessage] to find out about posts with no subscribed
 * mailboxes.
 */
data class ReturnedMessage<T>(
    val bus: MagicBus,
    val message: T,
)
