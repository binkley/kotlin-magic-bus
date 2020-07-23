package hm.binkley.labs

import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors.toList
import java.util.stream.Stream

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

    companion object {
        @Suppress("FunctionMinLength")
        fun of(
            returned: (ReturnedMessage) -> Unit,
            failed: (FailedMessage) -> Unit,
            observed: (Mailbox<*>, Any) -> Unit
        ) = SimpleMagicBus(returned, failed, observed)

        private fun record(deliveries: AtomicInteger): Consumer<Mailbox<*>> =
            Consumer { deliveries.incrementAndGet() }
    }
}

internal class Subscribers {
    private val subscriptions:
        MutableMap<Class<Any>, MutableSet<Mailbox<Any>>> =
            ConcurrentSkipListMap { a, b -> classOrder(a, b) }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    internal fun <T> subscribe(
        messageType: Class<T>,
        mailbox: Mailbox<in T>
    ) {
        subscriptions.computeIfAbsent(messageType as Class<Any>) {
            mailboxes()
        }.add(mailbox as Mailbox<Any>)
    }

    @Synchronized
    internal fun unsubscribe(
        messageType: Class<*>,
        mailbox: Mailbox<*>
    ) {
        val mailboxes = subscriptions[messageType]
            ?: throw NoSuchElementException()
        if (!mailboxes.remove(mailbox)) throw NoSuchElementException()
    }

    @Suppress("FunctionMinLength")
    @Synchronized
    internal fun of(messageType: Class<Any>): Stream<Mailbox<Any>> {
        return subscriptions.entries.stream()
            .filter(subscribedTo(messageType))
            .flatMap(toMailboxes())
    }

    companion object {
        private fun <T> mailboxes(): MutableSet<Mailbox<T>> =
            CopyOnWriteArraySet()

        private fun subscribedTo(
            messageType: Class<*>
        ): Predicate<Map.Entry<Class<*>, Set<Mailbox<*>>>> {
            return Predicate { e: Map.Entry<Class<*>, Set<Mailbox<*>>> ->
                e.key.isAssignableFrom(messageType)
            }
        }
    }
}

private fun toMailboxes():
    (Map.Entry<Class<Any>, Set<Mailbox<Any>>>) -> Stream<Mailbox<Any>> =
        { e: Map.Entry<Class<Any>, Set<Mailbox<Any>>> -> e.value.stream() }

private fun classOrder(a: Class<*>, b: Class<*>): Int {
    val aFirst = b.isAssignableFrom(a)
    val bFirst = a.isAssignableFrom(b)
    return aFirst.compareTo(bFirst)
}
