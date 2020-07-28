package hm.binkley.labs

/**
 * A _simple_ implementation of [MagicBus].  Limitations include:
 * * This is not a thread-safe implementation
 * * Single-threaded [post] &mdash; callers _block_ until all mailboxes
 *   process the message
 * * No loop detection &mdash; no attempt is made to prevent "storms" whereby
 *   a single post results in mailboxes posting additional messages, possibly
 *   without limits
 */
class SimpleMagicBus(
    /** A callback for successful posts with no mailboxes. */
    private val returned: (ReturnedMessage) -> Unit,
    /** A callback from posts raising [Exception] from their mailboxes. */
    private val failed: (FailedMessage) -> Unit,
    /** A callback for successful posts delivered to each mailbox. */
    private val delivered: (Mailbox<*>, Any) -> Unit,
) : MagicBus {
    private val subscribers = Subscribers()

    override fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) = subscribers.subscribe(messageType, mailbox)

    override fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) = subscribers.unsubscribe(messageType, mailbox)

    /**
     * Posts [message] to any subscribed mailboxes.
     *
     * Successful posts with mailboxes call [delivered]; those with no
     * mailbox call [returned] with details. [RuntimeException]s bubble
     * out; other [Exception]s call [failed] with details.
     */
    override fun post(message: Any) {
        var deliveries = 0
        subscribers.of(message.javaClass).forEach { mailbox ->
            ++deliveries
            receive(mailbox, message)
        }
        returnIfDead(deliveries, message)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> subscribers(messageType: Class<T>): Sequence<Mailbox<T>> =
        subscribers.of(messageType as Class<Any>)
            .map { mailbox: Mailbox<*> -> mailbox as Mailbox<T> }

    /**
     * Helper to avoid requiring a class token.
     *
     * @see subscribers
     */
    inline fun <reified T> subscribers() = subscribers(T::class.java)

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T> receive(mailbox: Mailbox<T>, message: T) =
        try {
            delivered(mailbox, message as Any)
            mailbox(message)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            failed(FailedMessage(this, mailbox, message as Any, e))
        }

    private fun returnIfDead(deliveries: Int, message: Any) {
        if (0 == deliveries) returned(ReturnedMessage(this, message))
    }

    companion object
}

class OnReturn(private val returned: (ReturnedMessage) -> Unit) {
    inner class OnFailure(private val failed: (FailedMessage) -> Unit) {
        infix fun onDelivery(delivered: (Mailbox<*>, Any) -> Unit): MagicBus =
            SimpleMagicBus(returned, failed, delivered)
    }

    infix fun onFailure(failed: (FailedMessage) -> Unit) = OnFailure(failed)
}

/**
 * Provides a cleaner API for creating simple magic busses.  Example:
 * ```
 * SimpleMagicBus.onReturn { ... } onFailure { ... } onDelivery { ... }
 * ```
 */
fun SimpleMagicBus.Companion.onReturn(returned: (ReturnedMessage) -> Unit) =
    OnReturn(returned)

private class Subscribers {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        subscriptions.getOrPut(messageType as Class<Any>) {
            mutableSetOf()
        } += mailbox as Mailbox<Any>
    }

    @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        val mailboxes = subscriptions[messageType]
            ?: throw NoSuchElementException()
        if (!mailboxes.remove(mailbox)) throw NoSuchElementException()
    }

    @Suppress("FunctionMinLength")
    fun of(messageType: Class<Any>): Sequence<Mailbox<Any>> =
        subscriptions.entries.asSequence()
            .filter { it.key.isAssignableFrom(messageType) }
            .sortedWith { a, b ->
                b.key.isAssignableFrom(a.key)
                    .compareTo(a.key.isAssignableFrom(b.key))
            }
            .flatMap { it.value }
}
