package hm.binkley.labs

typealias Mailbox<T> = (T) -> Unit

interface MagicBus {
    fun <T> subscribe(messageType: Class<T>, mailbox: Mailbox<in T>)
    fun <T> unsubscribe(messageType: Class<T>, mailbox: Mailbox<in T>)
    fun post(message: Any)
}

data class FailedMessage(
    val bus: MagicBus,
    val mailbox: Mailbox<*>,
    val message: Any,
    val failure: Exception
)

data class ReturnedMessage(
    val bus: MagicBus,
    val message: Any
)

data class DiscardMailbox<T>(val type: Class<T>) : Mailbox<T> {
    override operator fun invoke(message: T) = Unit
}

inline fun <reified T> discard(): Mailbox<T> = DiscardMailbox(T::class.java)

fun <T, E : Exception> failWith(exceptionCtor: () -> E): Mailbox<T> =
    { throw exceptionCtor() }

inline fun <reified T> Mailbox<in T>.deliverTo(bus: MagicBus) =
    bus.subscribe(T::class.java, this)

inline fun <reified T> Mailbox<in T>.noDeliveryTo(bus: MagicBus) =
    bus.unsubscribe(T::class.java, this)
