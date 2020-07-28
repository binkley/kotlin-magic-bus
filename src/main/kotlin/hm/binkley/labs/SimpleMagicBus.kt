package hm.binkley.labs

/**
 * A _simple_ implementation of [MagicBus].  Limitations include:
 * * This is not a thread-safe implementation
 * * Single-threaded [post] &mdash; callers _block_ until all mailboxes
 *   process the message
 * * No loop detection &mdash; no attempt is made to prevent "storms" whereby
 *   a single post results in mailboxes posting additional messages, possibly
 *   without limits
 * * Default "do nothing" mailboxes for dead and rejected letters: subscribing
 *   for these is optional by the caller
 */
class SimpleMagicBus : MagicBus {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> = mutableMapOf()

    init {
        installDefaultMailboxes()
    }

    override fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        subscriptions.getOrPut(messageType as Class<Any>) {
            mutableSetOf()
        } += mailbox as Mailbox<Any>
    }

    override fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING", "UNCHECKED_CAST")
        subscriptions.getOrElse(messageType as Class<Any>) {
            throw NoSuchElementException()
        }.remove(mailbox) || throw NoSuchElementException()
    }

    override fun post(message: Any) {
        var deliveries = 0
        subscribers(message.javaClass).forEach { mailbox ->
            ++deliveries
            receive(mailbox, message)
        }
        returnIfDead(deliveries, message)
    }

    /**
     * Helper to avoid requiring a class token.
     *
     * @see subscribers
     */
    inline fun <reified T> subscribers() = subscribers(T::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T> subscribers(messageType: Class<T>): Sequence<Mailbox<T>> =
        subscriptions.entries.asSequence()
            .filter { it.key.isAssignableFrom(messageType) }
            .sortedWith { a, b ->
                b.key.isAssignableFrom(a.key)
                    .compareTo(a.key.isAssignableFrom(b.key))
            }
            .flatMap { it.value }
            .map { it as Mailbox<T> }

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T> receive(mailbox: Mailbox<T>, message: T) =
        try {
            mailbox(message)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            post(FailedMessage(this, mailbox, message, e))
        }

    private fun returnIfDead(deliveries: Int, message: Any) {
        if (0 == deliveries) post(ReturnedMessage(this, message))
    }

    private fun installDefaultMailboxes() {
        // Default do nothings: avoid stack overflow from reposting
        subscribe(object : Mailbox<ReturnedMessage<*>> {
            override fun invoke(message: ReturnedMessage<*>) = Unit
            override fun toString() = "DEFAULT-DEAD-LETTERBOX"
        })
        subscribe(object : Mailbox<FailedMessage<*>> {
            override fun invoke(message: FailedMessage<*>) = Unit
            override fun toString() = "DEFAULT-REJECTED-LETTERBOX"
        })
    }
}
