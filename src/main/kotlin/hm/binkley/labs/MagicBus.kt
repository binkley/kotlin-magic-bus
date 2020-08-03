package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * Represents a _minimal_ bus for an applications to self-communicate via
 * _messaging_ by types.
 */
interface MagicBus {
    /** Delivers future messages of [messageType] to [mailbox]. */
    fun <T> subscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Stops delivering future messages of [messageType] to [mailbox]. */
    fun <T> unsubscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Posts [message] to any subscribed mailboxes based on message type. */
    fun post(message: Any)
}

/**
 * Subscribes without caller providing a type object.
 *
 * @see MagicBus.subscribe
 */
inline fun <reified T> MagicBus.subscribe(noinline mailbox: Mailbox<in T>) =
    subscribe(T::class.java, mailbox)

/**
 * Unsubscribes without caller providing a type object.
 *
 * @see MagicBus.unsubscribe
 */
inline fun <reified T> MagicBus.unsubscribe(noinline mailbox: Mailbox<in T>) =
    unsubscribe(T::class.java, mailbox)

/**
 * Creates a mailbox which throws away messages of [messageType].  Use case:
 * To avoid [ReturnedMessage] posts for message types not of interest, use a
 * discard mailbox.
 *
 * *NB* &mdash; An extreme example is:
 * ```
 * bus.subscribe(discard<ReturnedMessage>())
 * ```
 * which ignores all messages that have no mailboxes.  However, this is an
 * _anti-pattern_, as it covers up bugs in typing hierarchies or messaging
 * pattern implementations.
 *
 * @see discard
 */
@Suppress("UnusedPrivateMember")
class DiscardMailbox<T>(private val messageType: Class<T>) : Mailbox<T> {
    override operator fun invoke(message: T) = Unit
    override fun toString() = "DISCARD-MAILBOX"
}

/**
 * Creates a discard mailbox without providing a type object.
 *
 * @see DiscardMailbox
 */
inline fun <reified T> discard(): Mailbox<T> = DiscardMailbox(T::class.java)

/** Creates a mailbox which always fails. */
fun <T, E : Throwable> failWith(exceptionCtor: () -> E): Mailbox<T> =
    { throw exceptionCtor() }

/**
 * A default, singleton bus.  The instance is a [SimpleMagicBus] with its
 * additional methods and restrictions.
 */
val DEFAULT_GLOBAL_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }
