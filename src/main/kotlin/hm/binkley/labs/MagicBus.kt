package hm.binkley.labs

interface Mailbox<T> {
    fun receive(message: T)
}

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
