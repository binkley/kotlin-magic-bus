package hm.binkley.labs

/**
 * A _simple_ implementation of [MagicBus].  Limitations include:
 * * No thread safety
 * * Single-threaded [post] &mdash; callers _block_ until all mailboxes
 *   process the message
 * * No loop detection &mdash; no attempt is made to prevent "storms" whereby
 *   a single post results in mailboxes posting additional messages, possibly
 *   without limits
 *
 * Upsides include:
 * * Guaranteed ordering: Subscribers to parent classes always receive
 *   a message before child class subscribers; the bus always sends
 *   [FailedMessage] notifications in the order in which mailboxes failed,
 *   interleaved with later subscribers to the original message
 *
 * Additional features include:
 * * [subscribers] provides a correct list of mailboxes for a given message
 *   type in the same order as message delivery
 * * By default, if there are no mailboxes for either [ReturnedMessage] or
 *   [FailedMessage], these are "dropped".  Code creating a new
 *   `SimpleMessageBus` is responsible for subscribing to these message types
 *
 * Example bus creation with handling of returned and failed messages:
 * ```
 * val failed = mutableListOf<FailedMessage<*>>()
 * val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()
 * val bus = SimpleMagicBus().apply {
 *     subscribe<ReturnedMessage<*>> {
 *         returned += it
 *     }
 *     subscribe<FailedMessage<*>> {
 *         failed += it
 *     }
 * }
 * ```
 */
class SimpleMagicBus : MagicBus {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> = mutableMapOf()

    init {
        installDefaultMailboxes()
    }

    override fun <T : Any> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        subscriptions.getOrPut(messageType as Class<Any>) {
            mutableSetOf()
        } += mailbox as Mailbox<Any>
    }

    override fun <T : Any> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val mailboxes = subscriptions.getOrElse(messageType as Class<Any>) {
            throw NoSuchElementException()
        }
        // TODO: Kotlin's "remove" is inlined and includes a type cast which
        //       cannot fail, hence JaCoCo's complaint of a missed branch
        //       It boils down to JDK's `Collection.remove` accepts `Object`
        //       rather than just "T".  Kotlin addresses this syntactically,
        //       but there is still a type check in the byte code, and JaCoCo
        //       is not clever enough to ignore that this check cannot fail
        @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
        if (!mailboxes.remove(mailbox)) throw NoSuchElementException()
    }

    override fun post(message: Any) {
        val mailboxes = subscribers(message.javaClass)
        if (mailboxes.isEmpty()) return post(ReturnedMessage(this, message))

        mailboxes.forEach { mailbox ->
            receive(mailbox, message)
        }
    }

    /**
     * Helper to avoid caller providing a class token.
     *
     * @see subscribers
     */
    inline fun <reified T> subscribers() = subscribers(T::class.java)

    /** Return the mailboxes which would receive message of [messageType]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> subscribers(messageType: Class<T>) = subscriptions.entries
        .filter { it.key.isAssignableFrom(messageType) }
        .sortedWith { a, b ->
            // TODO: Extract to explanatory function
            b.key.isAssignableFrom(a.key)
                .compareTo(a.key.isAssignableFrom(b.key))
        }
        .flatMap { it.value } as List<Mailbox<T>>

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T : Any> receive(mailbox: Mailbox<T>, message: T) =
        try {
            mailbox(message)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            post(FailedMessage(this, mailbox, message, e))
        }

    private fun installDefaultMailboxes() {
        // Default do nothings: avoid stack overflow from reposting
        subscribe(object : Mailbox<ReturnedMessage<Any>> {
            override fun invoke(message: ReturnedMessage<Any>) = Unit
            override fun toString() = "DEFAULT-DEAD-LETTERBOX"
        })
        subscribe(object : Mailbox<FailedMessage<Any>> {
            override fun invoke(message: FailedMessage<Any>) = Unit
            override fun toString() = "DEFAULT-REJECTED-LETTERBOX"
        })
    }
}
