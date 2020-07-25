package hm.binkley.labs

/** Subscribe to [FailedMessage] to find out about broken mailboxes. */
data class FailedMessage(
    val bus: MagicBus,
    val mailbox: Mailbox<*>,
    val message: Any,
    val failure: Exception
)
