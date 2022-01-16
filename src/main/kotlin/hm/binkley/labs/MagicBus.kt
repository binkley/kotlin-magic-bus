package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default singleton message bus on the current thread.  The instance is a
 * [SimpleMagicBus] suitable as a program-global bus.
 */
val CURRENT_THREAD_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/** A internal message bus for self-communication by message type. */
interface MagicBus {
    /** Current subscriptions listed by order of subscription. */
    val subscriptions: Map<Class<*>, List<Mailbox<*>>>

    /**
     * Lists subscribers which would receive a message of [messageType]
     * by order they would receive.
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
     * Posts [message] to any subscribed mailboxen based on message type.
     *
     * Note that:
     * - Subscribed mailboxes that fail produce [FailedMessage] posts
     * - Posts with no subscribers produce [ReturnedMessage] posts
     */
    fun post(message: Any)
}
