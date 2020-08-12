package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

/**
 * A default, singleton bus.  The instance is a [SimpleMagicBus] with its
 * additional methods and restrictions.
 */
val DEFAULT_GLOBAL_BUS: SimpleMagicBus by lazy { SimpleMagicBus() }

/**
 * Represents a _minimal_ bus for an applications to self-communicate via
 * _messaging_ by types.
 */
interface MagicBus {
    /** Delivers future messages of [messageType] to [mailbox]. */
    fun <T : Any> subscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Stops delivering future messages of [messageType] to [mailbox]. */
    fun <T : Any> unsubscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Posts [message] to any subscribed mailboxes based on message type. */
    fun post(message: Any)
}

/**
 * Subscribes [mailbox] to [this] bus without caller providing a type object.
 *
 * @see MagicBus.subscribe
 * @see subscribeTo
 */
inline fun <reified T : Any> MagicBus.subscribe(
    noinline mailbox: Mailbox<in T>,
) =
    subscribe(T::class.java, mailbox)

/** An alternative, fluent syntax to [subscribe]. */
inline fun <reified T : Any> Mailbox<T>.subscribeTo(bus: MagicBus): Mailbox<T> {
    bus.subscribe(this)
    return this
}

/**
 * Unsubscribes without caller providing a type object.
 *
 * @see MagicBus.unsubscribe
 */
inline fun <reified T : Any> MagicBus.unsubscribe(
    noinline mailbox: Mailbox<in T>,
) =
    unsubscribe(T::class.java, mailbox)

/** An alternative, fluent syntax to [unsubscribe]. */
inline fun <reified T : Any> Mailbox<T>.unsubscribeFrom(bus: MagicBus): Mailbox<T> {
    bus.unsubscribe(this)
    return this
}

/** Creates a mailbox wrapping [receive], and a `toString` of [name]. */
fun <T> mailbox(name: String, receive: Mailbox<T>) = object : Mailbox<T> {
    override fun invoke(message: T) = receive(message)
    override fun toString() = name
}

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
 */
inline fun <reified T : Any> discard(): Mailbox<T> =
    mailbox("DISCARD-MAILBOX") { }

// data class InvalidMailbox(
//    val bus: MagicBus,
//    val mailbox: Mailbox<*>,
//    val busMesage: Any
// ) : Exception("BUG: Mailbox $mailbox reposts $busMesage")
