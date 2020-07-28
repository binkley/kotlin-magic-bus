package hm.binkley.labs

/** Subscribe to [FailedMessage] to find out about broken mailboxes. */
data class FailedMessage<T>(
    val bus: MagicBus,
    val mailbox: Mailbox<T>,
    val message: T,
    val failure: Exception
)
