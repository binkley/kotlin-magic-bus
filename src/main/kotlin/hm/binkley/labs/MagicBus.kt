package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default, singleton bus, non-threadsafe.  The instance is a
 * [SimpleMagicBus] with its additional methods and restrictions.
 */
val DEFAULT_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/**
 * Represents a _minimal_, non-threadsafe bus for an applications to
 * self-communicate via messaging_ by types.
 *
 * Note that messages are delivered in _causal_ order, supertype receivers
 * before subtypes mailboxes, and in subscription order thereafter.
 */
interface MagicBus {
    /** Inspects current subscriptions. */
    val subscriptions: Map<Class<*>, Set<Mailbox<*>>>

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

    /** Posts [message] to any subscribed mailboxes based on message type. */
    fun post(message: Any)
}
