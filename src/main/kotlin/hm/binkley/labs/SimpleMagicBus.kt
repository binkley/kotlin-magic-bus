package hm.binkley.labs

import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors.toList
import java.util.stream.Stream

/**
 * A _simple_ implementation of [MagicBus].  Limitations include:
 * * Single-threaded [post] &mdash; callers _block_ until all mailboxes
 *   process the message
 * * No loop detection &mdash; no attempt is made to prevent "storms" whereby
 *   a single post results in mailboxes posting additional messages, possibly
 *   without limits
 */
class SimpleMagicBus(
    private val returned: (ReturnedMessage) -> Unit,
    private val failed: (FailedMessage) -> Unit,
    private val observed: (Mailbox<*>, Any) -> Unit
) : MagicBus {
    private val subscribers = Subscribers()

    override fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>
    ) = subscribers.subscribe(messageType, mailbox)

    override fun <T> unsubscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>
    ) = subscribers.unsubscribe(messageType, mailbox)

    override fun post(message: Any) =
        subscribers.of(message.javaClass).use { mailboxes ->
            val deliveries = AtomicInteger()

            mailboxes.onClose {
                returnIfDead(deliveries, message)
            }
                .peek(record(deliveries))
                .forEach(receive(message))
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> subscribers(messageType: Class<T>): List<Mailbox<in T>> =
        subscribers.of(messageType as Class<Any>)
            .map { mailbox: Mailbox<*> -> mailbox as Mailbox<T> }
            .collect(toList())

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private fun <T> receive(message: T): Consumer<Mailbox<T>> {
        return Consumer { mailbox: Mailbox<T> ->
            try {
                observed(mailbox, message as Any)
                mailbox(message)
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                failed(FailedMessage(this, mailbox, message as Any, e))
            }
        }
    }

    private fun returnIfDead(deliveries: AtomicInteger, message: Any) {
        if (0 == deliveries.get()) {
            returned(ReturnedMessage(this, message))
        }
    }
}

private class Subscribers {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> =
            ConcurrentSkipListMap { a, b -> messageTypeOrder(a, b) }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>
    ) {
        subscriptions.computeIfAbsent(messageType as Class<Any>) {
            mailboxes()
        }.add(mailbox as Mailbox<Any>)
    }

    @Synchronized
    fun unsubscribe(
        messageType: Class<*>,
        mailbox: Mailbox<*>
    ) {
        val mailboxes = subscriptions[messageType]
            ?: throw NoSuchElementException()
        if (!mailboxes.remove(mailbox)) throw NoSuchElementException()
    }

    @Suppress("FunctionMinLength")
    @Synchronized
    fun of(messageType: Class<Any>): Stream<Mailbox<Any>> =
        subscriptions.entries.stream()
            .filter(subscribedTo(messageType))
            .flatMap(toMailboxes())

    companion object {
        private fun <T> mailboxes(): MutableSet<Mailbox<T>> =
            CopyOnWriteArraySet()

        private fun subscribedTo(messageType: Class<*>) =
            { e: Map.Entry<Class<*>, Set<Mailbox<*>>> ->
                e.key.isAssignableFrom(messageType)
            }
    }
}

private fun toMailboxes():
    (Map.Entry<Class<Any>, Set<Mailbox<Any>>>) -> Stream<Mailbox<Any>> =
        { e: Map.Entry<Class<Any>, Set<Mailbox<Any>>> -> e.value.stream() }

private fun messageTypeOrder(a: Class<*>, b: Class<*>) =
    b.isAssignableFrom(a).compareTo(a.isAssignableFrom(b))

private fun record(deliveries: AtomicInteger): Consumer<Mailbox<*>> =
    Consumer { deliveries.incrementAndGet() }
