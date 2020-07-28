package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    private val returned = mutableListOf<ReturnedMessage<*>>()
    private val failed = mutableListOf<FailedMessage<*>>()
    private val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()

    private val bus = SimpleMagicBus().apply {
        subscribe<ReturnedMessage<*>> {
            returned += it
        }
        subscribe<FailedMessage<*>> {
            failed += it
        }
    }

    @Test
    fun `should receive correct type`() {
        val mailbox = testMailbox<RightType>()
        bus.subscribe(mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should deliver correctly to disparate subscribers`() {
        val mailboxForDelivery = testMailbox<RightType>()
        bus.subscribe(mailboxForDelivery)
        val mailboxForNoDelivery = testMailbox<LeftType>()
        bus.subscribe(mailboxForNoDelivery)

        assertThat(bus.subscribers<RightType>().toList())
            .isEqualTo(listOf(mailboxForDelivery))

        val message = RightType()

        bus.post(message)

        assertOn(mailboxForDelivery)
            .delivered(message)
            .noneReturned()
            .noneFailed()
        assertOn(mailboxForNoDelivery)
            .noneDelivered()
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should not receive wrong type`() {
        val mailbox = testMailbox<LeftType>()
        bus.subscribe(mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = testMailbox<BaseType>()
        bus.subscribe(mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should save dead letters`() {
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun `should save failed posts`() {
        val reason = Exception()
        val mailbox: Mailbox<LeftType> = failWith { reason }
        bus.subscribe(mailbox)
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .noneReturned()
            .failed(with(mailbox, message, reason))
    }

    @Test
    fun `should bubble out runtime exceptions`() {
        val reason = RuntimeException()
        assertThatThrownBy {
            val mailbox: Mailbox<LeftType> = failWith { reason }
            bus.subscribe(mailbox)

            bus.post(LeftType())
        }.isSameAs(reason)
    }

    @Test
    fun `should bubble out JVM errors`() {
        val reason = Error()
        assertThatThrownBy {
            val mailbox: Mailbox<LeftType> = failWith { reason }
            bus.subscribe(mailbox)

            bus.post(LeftType())
        }.isSameAs(reason)
    }

    @Test
    fun `should receive earlier subscribers first`() {
        val delivery = AtomicInteger()
        val first = AtomicInteger()
        val second = AtomicInteger()
        val third = AtomicInteger()
        val fourth = AtomicInteger()
        bus.subscribe(record<RightType>(delivery, first))
        bus.subscribe(record<RightType>(delivery, second))
        bus.subscribe(record<RightType>(delivery, third))
        bus.subscribe(record<RightType>(delivery, fourth))

        bus.post(RightType())

        assertThat(first.get()).isEqualTo(0)
        assertThat(second.get()).isEqualTo(1)
        assertThat(third.get()).isEqualTo(2)
        assertThat(fourth.get()).isEqualTo(3)
        assertOn(noMailbox<Any>())
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should receive parent types first`() {
        val delivery = AtomicInteger()
        val farRight = AtomicInteger()
        val right = AtomicInteger()
        val base = AtomicInteger()
        val anythingElse = AtomicInteger()
        bus.subscribe(record<RightType>(delivery, right))
        bus.subscribe(record<FarRightType>(delivery, farRight))
        bus.subscribe(record<Any>(delivery, anythingElse))
        bus.subscribe(record<BaseType>(delivery, base))

        bus.post(FarRightType())

        assertThat(anythingElse.get()).isEqualTo(0)
        assertThat(base.get()).isEqualTo(1)
        assertThat(right.get()).isEqualTo(2)
        assertThat(farRight.get()).isEqualTo(3)
        assertOn(noMailbox<Any>())
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should unsubscribe exact type`() {
        val mailbox = testMailbox<LeftType>()
        bus.subscribe(mailbox)
        bus.unsubscribe(mailbox)
        val message = LeftType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxForDelivery = testMailbox<RightType>()
        val mailboxForNoDelivery: Mailbox<RightType> =
            failWith { Exception() }
        bus.subscribe(mailboxForDelivery)
        bus.subscribe(mailboxForNoDelivery)
        bus.unsubscribe(mailboxForNoDelivery)
        val message = RightType()

        bus.post(message)

        assertOn(mailboxForDelivery)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should fail to unsubscribe exact mailbox`() {
        val mailbox: Mailbox<RightType> = discard()

        assertThrows<NoSuchElementException> {
            bus.unsubscribe(mailbox)
        }
    }

    @Test
    fun `should fail to unsubscribe exact mailbox regardless of other mailboxes`() {
        val mailboxSubscribed = testMailbox<RightType>()
        val mailboxNotSubscribed: Mailbox<RightType> = discard()

        bus.subscribe(mailboxSubscribed)

        // TODO: Why isn't this raising branch coverage?
        assertThrows<NoSuchElementException> {
            bus.unsubscribe(mailboxNotSubscribed)
        }
    }

    @Test
    fun `should provide subscribers for message type in base-up order`() {
        // Subscription in child-parent order to show that the subscriber
        // list is in parent-child order
        val mailboxRight: Mailbox<RightType> = { }
        bus.subscribe(mailboxRight)
        val mailboxBase: Mailbox<BaseType> = { }
        bus.subscribe(mailboxBase)

        assertThat(bus.subscribers<RightType>().toList())
            .isEqualTo(listOf(mailboxBase, mailboxRight))
    }

    @Test
    fun `should provide accurate details on dead letters`() {
        val message = RightType()

        bus.post(message)

        val dead = returned[0]
        assertThat(dead.bus).isSameAs(bus)
        assertThat(dead.message).isSameAs(message)
    }

    @Test
    fun `should have default rejected letter box`() {
        assertThat(bus.subscribers<FailedMessage<*>>().toList())
            .hasSize(2)
    }

    @Test
    fun `should deliver first to first subscriber for rejected messages`() {
        val subscribers = bus.subscribers<ReturnedMessage<*>>()

        assertThat(subscribers.first().toString()).isEqualTo(
            "DEFAULT-DEAD-LETTERBOX"
        )
    }

    @Test
    fun `should deliver first to first subscriber for failed messages`() {
        val subscribers = bus.subscribers<FailedMessage<*>>()

        assertThat(subscribers.first().toString()).isEqualTo(
            "DEFAULT-REJECTED-LETTERBOX"
        )
    }

    @Test
    fun `should discard in the discard letter box`() {
        // TODO: A less lame test
        bus.subscribe(discard<RightType>())

        bus.post(RightType())

        // Nothing happens
    }

    @Test
    fun `should provide accurate details on failed posts`() {
        val reason = Exception()
        val mailbox: Mailbox<RightType> = failWith { reason }
        bus.subscribe(mailbox)
        val message = RightType()

        bus.post(message)

        val failed = failed[0]
        assertThat(failed.bus).isSameAs(bus)
        assertThat(failed.mailbox).isSameAs(mailbox)
        assertThat(failed.message).isSameAs(message)
        assertThat(failed.failure).isSameAs(reason)
    }

    private fun <T> assertOn(delivered: List<T>) =
        AssertDelivery(returned, failed, delivered)

    private fun <T> assertOn(delivered: TestMailbox<T>) =
        assertOn(delivered.messages)

    private fun with(message: Any) = ReturnedMessage(bus, message)
    private fun <T> with(
        mailbox: Mailbox<T>,
        message: T,
        failure: Exception,
    ) = FailedMessage(bus, mailbox, message, failure)

    private inline fun <reified T> testMailbox(
        messages: MutableList<T> = ArrayList(1),
    ) = TestMailbox(T::class.java, messages)

    private inner class TestMailbox<T>(
        private val messageType: Class<T>,
        val messages: MutableList<T>,
    ) : Mailbox<T> {
        override operator fun invoke(message: T) {
            messages.add(message)
            delivered.getOrPut(this) {
                mutableListOf()
            } += (message as Any)
        }

        override fun toString() = "TEST-MAILBOX<${messageType.simpleName}>"
    }
}

private fun <T> noMailbox() = emptyList<T>()
private fun <T> record(
    order: AtomicInteger,
    record: AtomicInteger,
): Mailbox<T> = { record.set(order.getAndIncrement()) }

private abstract class BaseType
private class LeftType : BaseType()
private open class RightType : BaseType()
private class FarRightType : RightType()

private class AssertDelivery<T>(
    private val returned: List<ReturnedMessage<*>>,
    private val failed: List<FailedMessage<*>>,
    private val delivered: List<T>,
) {
    fun noneDelivered() = apply {
        assertThat(delivered).isEmpty()
    }

    fun <U : T?> delivered(vararg delivered: U) = apply {
        assertThat(this.delivered).containsExactly(*delivered)
    }

    fun noneReturned() = apply {
        assertThat(returned).isEmpty()
    }

    fun returned(vararg returned: ReturnedMessage<*>) = apply {
        assertThat(this.returned).containsExactly(*returned)
    }

    fun noneFailed() = apply {
        assertThat(failed).isEmpty()
    }

    fun failed(vararg failed: FailedMessage<*>) = apply {
        assertThat(this.failed).containsExactly(*failed)
    }
}
