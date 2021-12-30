package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default, singleton bus, non-threadsafe.  The instance is a
 * [SimpleMagicBus] with its additional methods and restrictions.
 *
 * Normal design would make `DEFAULT_BUS` a factory, however the point of
 * self-messaging within a single program implies a global object visible to
 * all classes.
 *
 * @todo Reconsider this being a global object, ie, API instead requires
 *       declaration of a global bus:
 * ```
 * val TheBus: MagicBus = SimpleMagicBus()
 * ```
 */
val DEFAULT_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/**
 * Represents a _minimal_, non-threadsafe bus for an applications to
 * self-communicate via messaging_ by types.
 *
 * Note that messages are delivered in _causal_ order, supertype receivers
 * before subtypes mailboxen, and in subscription order thereafter.
 */
interface MagicBus {
    /** Current subscriptions listed in FIFO order of receiving messages. */
    val subscriptions: Map<Class<*>, List<Mailbox<*>>>

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

    /** Posts [message] to any subscribed mailboxen based on message type. */
    fun post(message: Any)
}
