package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default singleton message bus on the current thread.  The instance is a
 * [SimpleMagicBus] suitable as a program-global bus.
 */
val CURRENT_THREAD_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/** An internal message bus for self-communication by message type. */
interface MagicBus {
    /** Current subscriptions listed by order of subscription. */
    val subscriptions: Map<Class<*>, List<Mailbox<*>>>

    /**
     * Lists subscribers which would receive a message of [messageType]
     * in the order by which they would receive it.
     */
    fun <T : Any> subscribersTo(messageType: Class<in T>): List<Mailbox<T>>

    /** Delivers messages of [messageType] to [mailbox]. */
    fun <T : Any> subscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    )

    /** Stops delivering messages of [messageType] to [mailbox]. */
    fun <T : Any> unsubscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    )

    /**
     * Posts [message] to any subscribed mailboxen based on the message type.
     *
     * Note that:
     * - Subscribed mailboxen that fail produce [FailedMessage] posts
     * - Posts with no subscribers produce [UndeliveredMessage] posts
     * - Unsubscribed [ReturnReceipt] posts do not generate undelivered posts
     */
    fun post(message: Any)
}
