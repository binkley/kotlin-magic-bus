package hm.binkley.labs

/**
 * Subscribe to [FailedMessage] to find out about broken mailboxes.  The bus
 * posts a separate message for each broken mailbox.
 *
 * *NB* &mdash; This does not capture `RuntimeException` or `Error` failures;
 * those immediately bubble out to the message poster as thrown exceptions.
 */
data class FailedMessage<T>(
    val bus: MagicBus,
    val mailbox: Mailbox<T>,
    val message: T,
    val failure: Exception
)
