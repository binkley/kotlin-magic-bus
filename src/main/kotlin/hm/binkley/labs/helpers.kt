package hm.binkley.labs

/**
 * Subscribes [mailbox] to [this] bus without caller providing a type object.
 *
 * @see MagicBus.subscribe
 * @see subscribeTo
 */
inline fun <reified T : Any> MagicBus.subscribe(
    noinline mailbox: Mailbox<in T>,
) = subscribe(T::class.java, mailbox)

/** An alternative, fluent syntax to [subscribe]. */
inline fun <reified T : Any, M : Mailbox<T>> M.subscribeTo(
    bus: MagicBus,
): M {
    bus.subscribe(this)
    return this
}

/**
 * Unsubscribes without caller providing a type object.
 *
 * @see MagicBus.unsubscribe
 */
inline fun <reified T : Any> MagicBus.unsubscribe(
    noinline mailbox: Mailbox<in T>,
) = unsubscribe(T::class.java, mailbox)

/** An alternative, fluent syntax to [unsubscribe]. */
inline fun <reified T : Any, M : Mailbox<T>> M.unsubscribeFrom(
    bus: MagicBus,
): M {
    bus.unsubscribe(this)
    return this
}

/** Creates a mailbox wrapping [receive], and a `toString` of [name]. */
fun <T> namedMailbox(name: String, receive: Mailbox<T>) =
    object : Mailbox<T> {
        override fun invoke(message: T) = receive(message)
        override fun toString() = name
    }

/**
 * Creates a mailbox which throws away messages.  Use case: To avoid
 * [ReturnedMessage] posts for message types not of interest, use a
 * discard mailbox.
 *
 * *NB* &mdash; An extreme example is:
 * ```
 * bus.subscribe(discard<ReturnedMessage>())
 * ```
 * which ignores all messages that have no mailboxes.  However, this is an
 * _anti-pattern_, as it covers up bugs in typing hierarchies or messaging
 * pattern implementations.
 */
inline fun <reified T : Any> discard(): Mailbox<T> =
    namedMailbox("DISCARD-MAILBOX") { }
