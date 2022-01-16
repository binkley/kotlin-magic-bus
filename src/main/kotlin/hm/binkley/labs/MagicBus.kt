package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default singleton bus on the current thread.  The instance is a
 * [SimpleMagicBus].
 */
val CURRENT_THREAD_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/**
 * Represents a _minimal_, non-threadsafe bus for an applications to
 * self-communicate via messaging_ by types.
 *
 * Note that messages are delivered in _causal_ order, supertype receivers
 * before subtypes mailboxen, and in subscription order thereafter.
 */
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
