package hm.binkley.labs

/**
 * A _simple_ implementation of [MagicBus].  Limitations include:
 * * No thread safety
 * * Single-threaded [post] &mdash; callers _block_ until all mailboxen
 *   process the message
 * * No loop detection &mdash; no attempt is made to prevent "storms" whereby
 *   a single post results in mailboxen posting additional messages, possibly
 *   without limits
 *
 * Upsides include:
 * * Guaranteed ordering: Subscribers to parent classes always receive
 *   a message before child class subscribers; the bus always sends
 *   [FailedMessage] notifications in the order in which mailboxen failed,
 *   interleaved with later subscribers to the original message
 *
 * Additional features include:
 * * [subscribersTo] provides a correct list of mailboxen for a given message
 *   type in the same order as message delivery
 * * By default, if there are no mailboxen for either [ReturnedMessage] or
 *   [FailedMessage]; the result is a `StackOverflowError` in these cases.
 *   Code creating a new `SimpleMessageBus` is responsible for subscribing
 *   to these message types
 *
 * Example bus creation with handling of returned and failed messages
 * (alternatively, extend the class and encapsulate subscriptions in `init`):
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
open class SimpleMagicBus : MagicBus {
    private val _subscriptions =
        mutableMapOf<Class<*>, MutableList<Mailbox<*>>>()

    init {
        installFallbackMailboxen()
    }

    override val subscriptions: Map<Class<*>, List<Mailbox<*>>>
        get() = _subscriptions

    override fun <T : Any> subscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    ) {
        _subscriptions.getOrPut(messageType) {
            mutableListOf()
        } += mailbox
    }

    override fun <T : Any> unsubscribe(
        messageType: Class<in T>,
        mailbox: Mailbox<in T>,
    ) {
        val mailboxen = _subscriptions.getOrElse(messageType) {
            throw NoSuchElementException()
        }

        if (!mailboxen.remove(mailbox)) throw NoSuchElementException()
        if (mailboxen.isEmpty()) _subscriptions.remove(messageType)
    }

    override fun post(message: Any) {
        val mailboxen = subscribersTo(message.javaClass)
        if (mailboxen.isEmpty()) return post(ReturnedMessage(this, message))

        mailboxen.forEach { it.post(message) }
    }

    /** Return the mailboxen which would receive message of [messageType]. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> subscribersTo(messageType: Class<in T>) =
        _subscriptions.entries.filter { it.key.isAssignableFrom(messageType) }
            // TODO: Moving the sort into the map leads to ClassCastException;
            //  the filter is needed to prevent this.  There is no defined
            //  ordering between unrelated classes
            .sortedWith { a, b -> parentFirstAndFifoOrdering(a.key, b.key) }
            .flatMap { it.value } as List<Mailbox<T>>

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T : Any> Mailbox<in T>.post(message: T) = try {
        this(message)
    } catch (e: RuntimeException) {
        // NB -- `RuntimeException` is a subtype of `Exception` No need to
        // handle `Error`: it is not a subtype, and catching `Error` leads to
        // bad mojo; see
        // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Error.html
        throw e
    } catch (e: Exception) {
        post(FailedMessage(this@SimpleMagicBus, this, message, e))
    }

    /**
     * Add fallback do-nothing mailboxen for [ReturnedMessage] and
     * [FailedMessage].  This avoids stack overflow from reposting if user
     * does not install mailboxen for them, or if user mailboxen are
     * themselves faulty.
     */
    private fun installFallbackMailboxen() {
        subscribe(discard<ReturnedMessage<*>>())
        subscribe(discard<FailedMessage<*>>())
    }
}

// Notes:
// * Invert natural order so that parents come first
// * Ordering is stable so that FIFO on ties
// * Boolean sorts with `false` coming before `true`
private fun parentFirstAndFifoOrdering(a: Class<*>, b: Class<*>) =
    b.isAssignableFrom(a).compareTo(a.isAssignableFrom(b))
