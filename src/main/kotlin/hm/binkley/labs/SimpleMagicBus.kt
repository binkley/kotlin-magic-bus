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
 *   [FailedMessage]; the result is a `StackOverflowError` in these cases.
 *   Code creating a new `SimpleMessageBus` is responsible for subscribing
 *   to these message types
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
    private val subscriptions =
        mutableMapOf<Class<*>, MutableSet<Mailbox<*>>>()

    init {
        installFallbackMailboxes()
    }

    override fun <T : Any> subscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    ) {
        subscriptions.getOrPut(messageType) {
            mutableSetOf()
        } += mailbox
    }

    override fun <T : Any> unsubscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    ) {
        val mailboxes = subscriptions.getOrElse(messageType) {
            throw NoSuchElementException()
        }

        if (!mailboxes.remove(mailbox)) throw NoSuchElementException()
    }

    override fun post(message: Any) {
        val mailboxes = subscribers(message.javaClass)
        if (mailboxes.isEmpty()) return post(ReturnedMessage(this, message))

        mailboxes.forEach { it.post(message) }
    }

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T : Any> Mailbox<in T>.post(message: T) =
        try {
            this(message)
        } catch (e: RuntimeException) {
            // NB -- `RuntimeException` is a subtype of `Exception`
            // No need to handle `Error`: it is not a subtype
            throw e
        } catch (e: Exception) {
            post(FailedMessage(this@SimpleMagicBus, this, message, e))
        }

    /** Return the mailboxes which would receive message of [messageType]. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> subscribers(messageType: Class<in T>) = subscriptions.entries
        .filter { it.key.isAssignableFrom(messageType) }
        // TODO: Moving the sort into the map leads to ClassCastException; the
        //       filter is needed to prevent this.  There is no defined
        //       ordering between unrelated classes
        .sortedWith { a, b -> parentFirstAndFifoOrdering(a.key, b.key) }
        .flatMap { it.value } as List<Mailbox<T>>

    private fun installFallbackMailboxes() {
        // Default do nothings: avoid stack overflow from reposting
        subscribe(discard<ReturnedMessage<*>>())
        subscribe(discard<FailedMessage<*>>())
    }
}

// Notes:
// * Inverted order so that parents come first
// * Ordering is stable so that FIFO on ties
// * Boolean sorts with `false` coming before `true`
private fun parentFirstAndFifoOrdering(a: Class<*>, b: Class<*>) =
    b.isAssignableFrom(a).compareTo(a.isAssignableFrom(b))
