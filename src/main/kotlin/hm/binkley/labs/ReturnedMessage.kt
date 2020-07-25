package hm.binkley.labs

/** Subscribe to [ReturnedMessage] to find out about posts with no mailbox. */
data class ReturnedMessage(
    val bus: MagicBus,
    val message: Any
)
