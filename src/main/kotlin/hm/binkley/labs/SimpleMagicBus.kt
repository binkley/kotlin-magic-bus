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
class SimpleMagicBus : MagicBus {
    private val subscribers = Subscribers()

    init {
        // Default do nothings: avoid stack overflow from reposting
        subscribe(object : Mailbox<ReturnedMessage> {
            override fun invoke(p1: ReturnedMessage) = Unit
            override fun toString() = "DEFAULT-DEAD-LETTERBOX"
        })
        subscribe(object : Mailbox<FailedMessage> {
            override fun invoke(p1: FailedMessage) = Unit
            override fun toString() = "DEFAULT-REJECTED-LETTERBOX"
        })
    }

    override fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) = subscribers.subscribe(messageType, mailbox)

    override fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) = subscribers.unsubscribe(messageType, mailbox)

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
            mailbox(message)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            post(FailedMessage(this, mailbox, message as Any, e))
        }

    private fun returnIfDead(deliveries: Int, message: Any) {
        if (0 == deliveries) post(ReturnedMessage(this, message))
    }

    companion object
}

private class Subscribers {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> = mutableMapOf()

    fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        subscriptions.getOrPut(messageType as Class<Any>) {
            mutableSetOf()
        } += mailbox as Mailbox<Any>
    }

    fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING", "UNCHECKED_CAST")
        subscriptions.getOrElse(messageType as Class<Any>) {
            throw NoSuchElementException()
        }.remove(mailbox) || throw NoSuchElementException()
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
