package hm.binkley.labs

import lombok.Generated

typealias Mailbox<T> = (T) -> Unit

interface MagicBus {
    /** Delivers messages of [messageType] to [mailbox]. */
    fun <T> subscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Stops delivering messages of [messageType] to [mailbox]. */
    fun <T> unsubscribe(messageType: Class<T>, mailbox: Mailbox<in T>)

    /** Posts [message] to any subscribed mailboxes. */
    fun post(message: Any)
}

/**
 * Subscribes without caller providing a class object.
 *
 * @see MagicBus.subscribe
 */
inline fun <reified T> Mailbox<in T>.deliverFrom(bus: MagicBus) =
    bus.subscribe(T::class.java, this)

/**
 * Unsubscribes without caller providing a class object.
 *
 * @see MagicBus.unsubscribe
 */
inline fun <reified T> Mailbox<in T>.noDeliveryFrom(bus: MagicBus) =
    bus.unsubscribe(T::class.java, this)

/** Creates a mailbox which throws away messages of [messageType]. */
@Generated // Lie to JaCoCo
data class DiscardMailbox<T>(val messageType: Class<T>) : Mailbox<T> {
    override operator fun invoke(message: T) = Unit
}

/** Creates a [DiscardMailbox] without providing a class object. */
inline fun <reified T> discard(): Mailbox<T> = DiscardMailbox(T::class.java)

/** Creates a mailbox which always fails. */
fun <T, E : Exception> failWith(exceptionCtor: () -> E): Mailbox<T> =
    { throw exceptionCtor() }
