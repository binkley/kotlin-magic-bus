package hm.binkley.labs

/**
 * A _simple_ implementation of [MagicBus] designed for extending.
 *
 * Limitations include:
 * * No thread safety outside the current thread
 * * Each receiver is called in sequence &mdash; there is no parallelism
 * * Messages are delivered in _causal_ order, supertype receivers
 *   before subtypes mailboxen, and in subscription order thereafter
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
 * Also consider:
 * * Unhandled [UndeliveredMessage] and [FailedMessage] are silently
 *   discarded: you need to add subscribers for these
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

    // TODO: Moving the sort into the map leads to ClassCastException;
    //  the filter is needed to prevent this.
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> subscribersTo(messageType: Class<in T>) =
        _subscriptions.entries.filter { it.key.isAssignableFrom(messageType) }
            .sortedWith { a, b -> orderByParentElseFifo(a.key, b.key) }
            .flatMap { it.value } as List<Mailbox<T>>

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

        if (mailboxen.isNotEmpty()) {
            val received = mailboxen.map { it.post(message) }.any { it }
            if (received && message !is WithoutReceipt)
                post(ReturnReceipt(this, message))
        } else
            post(UndeliveredMessage(this, message))
    }

    /**
     * `RuntimeException` is a subtype of `Exception`.
     * No need to handle `Error`: it is not a subtype, and catching `Error`
     * leads to bad mojo (this includes things like JVM errors).
     * So rethrow `RuntimeException`, and send `Exception` as a failed message.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> Mailbox<in T>.post(message: T & Any): Boolean = try {
        this(message)
        true
    } catch (e: RuntimeException) {
        throw e
    } catch (e: Exception) {
        post(FailedMessage(this@SimpleMagicBus, this, message, e))
        false
    }

    /**
     * Add fallback do-nothing mailboxen for:
     * - [ReturnReceipt]
     * - [UndeliveredMessage]
     * - [FailedMessage].
     * This avoids stack overflow from reposting if the user themselves does
     * not install mailboxen for these message types, or if the user
     * mailboxen are themselves faulty (raising exceptions, or reposting
     * received message types without a way to eventually halt).
     */
    private fun installFallbackMailboxen() {
        subscribe(discard<ReturnReceipt<*>>())
        subscribe(discard<UndeliveredMessage<*>>())
        subscribe(discard<FailedMessage<*>>())
    }
}

/**
 * Notes:
 * * Invert natural order so that parents come first -- [b] before [a]
 * * Ordering is stable so that FIFO on ties
 * * `Boolean` sorts with `false` coming before `true`
 */
private fun orderByParentElseFifo(a: Class<*>, b: Class<*>) =
    b.isAssignableFrom(a).compareTo(a.isAssignableFrom(b))
