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

/** Fluent alternative syntax to [subscribe]. */
inline infix fun <reified T : Any, M : Mailbox<in T>> M.subscribeTo(
    bus: MagicBus,
): M {
    bus += this
    return this
}

/** A syntactic alternative to [subscribe]. */
inline operator fun <reified T : Any, M : Mailbox<in T>> MagicBus.plusAssign(
    mailbox: M,
) = subscribe(mailbox)

/**
 * Unsubscribes without caller providing a type object.
 *
 * @see MagicBus.unsubscribe
 * @see unsubscribeFrom
 */
inline fun <reified T : Any> MagicBus.unsubscribe(
    noinline mailbox: Mailbox<in T>,
) = unsubscribe(T::class.java, mailbox)

/** A fluent alternative syntax to [unsubscribe]. */
inline infix fun <reified T : Any, M : Mailbox<in T>> M.unsubscribeFrom(
    bus: MagicBus,
): M {
    bus -= this
    return this
}

/** A syntactic alternative to [unsubscribe]. */
inline operator fun <reified T : Any, M : Mailbox<in T>> MagicBus.minusAssign(
    mailbox: M,
) = unsubscribe(mailbox)

/** Creates a mailbox wrapping [receive], and a [toString] of [name]. */
fun <T> namedMailbox(name: String, receive: Mailbox<in T>) =
    object : Mailbox<T> {
        override fun invoke(message: T) = receive(message)
        override fun toString() = name
    }

/**
 * Creates a mailbox which throws away messages.
 * An example use case: To ignore posts for messages of type [T], use a discard
 * mailbox.
 */
inline fun <reified T : Any> discard(): Mailbox<T> =
    namedMailbox("DISCARD-MAILBOX<${T::class.java.simpleName}>") { }

/**
 * Lists [Mailbox]en without caller providing a type object.
 *
 * @see MagicBus.subscribersTo
 */
inline fun <reified T : Any> MagicBus.subscribersTo() =
    subscribersTo(T::class.java)

/** Fluent alternative syntax to [MagicBus.post]. */
infix fun Any.postTo(bus: MagicBus) = bus.post(this)
