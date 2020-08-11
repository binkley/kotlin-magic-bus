package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    private val returned = mutableListOf<ReturnedMessage<*>>()
    private val failed = mutableListOf<FailedMessage<*>>()
    private val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()

    private val bus = SimpleMagicBus().apply {
        subscribe<ReturnedMessage<Any>> {
            returned += it
        }
        subscribe<FailedMessage<Any>> {
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
            .deliveredInOrder(message)
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
            .deliveredInOrder(message)
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
            .returnedInOrder(with(message))
            .noneFailed()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = testMailbox<BaseType>()
        bus.subscribe(mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should save dead letters`() {
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .returnedInOrder(with(message))
            .noneFailed()
    }

    @Test
    fun `should provide distinct discard mailboxes`() {
        assertThat(discard<RightType>()).isNotEqualTo(discard<RightType>())
    }

    @Test
    fun `should provide distinct failure mailboxes`() {
        assertThat(failWith<RightType, Exception> { Exception() })
            .isNotEqualTo(failWith<RightType, Exception> { Exception() })
    }

    @Test
    fun `should save failed posts`() {
        val firstReason = Exception()
        val firstBrokenMailbox: Mailbox<LeftType> = failWith { firstReason }
        bus.subscribe(firstBrokenMailbox)
        val secondReason = Exception()
        val secondBrokenMailbox: Mailbox<LeftType> = failWith { secondReason }
        bus.subscribe(secondBrokenMailbox)
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .noneReturned()
            .failedInOrder(
                with(firstBrokenMailbox, message, firstReason),
                with(secondBrokenMailbox, message, secondReason)
            )
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
    fun `should post other exceptions and receive them`() {
        val reason = Exception()
        val badMailbox: Mailbox<RightType> = failWith { reason }
        bus.subscribe(badMailbox)
        val allMailbox = testMailbox<Any>()
        bus.subscribe(allMailbox)

        val message = RightType()
        val failure = with(badMailbox, message, reason)

        bus.post(message)

        assertOn(allMailbox)
            .noneReturned()
            .failedInOrder(failure)
            .deliveredInOrder(message, failure)
    }

    @Test
    fun `should be fatal for a failure mailbox to itself fail`() {
        val badMailbox: Mailbox<RightType> = failWith { Exception() }
        bus.subscribe(badMailbox)
        val fatalMailbox: Mailbox<FailedMessage<*>> = failWith { Exception() }
        bus.subscribe(fatalMailbox)

        assertThrows<StackOverflowError> {
            bus.post(RightType())
        }
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
        val mailbox = discard<LeftType>()
        bus.subscribe(mailbox)

        bus.unsubscribe(mailbox)

        assertThat(bus.subscribers<LeftType>()).isEmpty()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxForDelivery = discard<RightType>()
        bus.subscribe(mailboxForDelivery)
        val mailboxForNoDelivery = discard<RightType>()
        bus.subscribe(mailboxForNoDelivery)

        bus.unsubscribe(mailboxForNoDelivery)

        assertThat(bus.subscribers<RightType>())
            .isEqualTo(listOf(mailboxForDelivery))
    }

    @Test
    fun `should fail to unsubscribe for message type`() {
        val mailbox = discard<RightType>()

        assertThrows<NoSuchElementException> {
            bus.unsubscribe(mailbox)
        }
    }

    @Test
    fun `should fail to unsubscribe exact mailbox regardless of other mailboxes`() {
        val mailboxSubscribed = discard<RightType>()
        val mailboxNotSubscribed = discard<RightType>()

        bus.subscribe(mailboxSubscribed)

        assertThrows<NoSuchElementException> {
            bus.unsubscribe(mailboxNotSubscribed)
        }
    }

    @Test
    fun `should provide subscribers for message type in base-up order`() {
        // Subscription in derived-base order to show that the subscriber
        // list is in base-derived order
        val mailboxRight = testMailbox<RightType>()
        bus.subscribe(mailboxRight)
        val mailboxBase = testMailbox<BaseType>()
        bus.subscribe(mailboxBase)

        assertThat(bus.subscribers<RightType>().toList())
            .isEqualTo(listOf(mailboxBase, mailboxRight))
    }

    @Test
    fun `should provide subscribers for message type in subscribed-order`() {
        val mailboxes = (1..10).map {
            val mailbox = testMailbox<RightType>()
            bus.subscribe(mailbox)
            mailbox
        }

        // It is unusual to test the test code, but the production test relies
        // critically on test mailbox instances being non-equal
        assertThat(testMailbox<RightType>())
            .isNotEqualTo(testMailbox<RightType>())
        assertThat(bus.subscribers<RightType>().toList()).isEqualTo(mailboxes)
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
        assertThat(bus.subscribers<FailedMessage<Any>>().toList())
            .hasSize(2)
    }

    @Test
    fun `should deliver first to first subscriber for rejected messages`() {
        val subscribers = bus.subscribers<ReturnedMessage<Any>>()

        assertThat(subscribers.first().toString()).isEqualTo(
            "DEFAULT-DEAD-LETTERBOX"
        )
    }

    @Test
    fun `should deliver first to first subscriber for failed messages`() {
        val subscribers = bus.subscribers<FailedMessage<Any>>()

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
    private fun <T : Any> with(
        mailbox: Mailbox<T>,
        message: T,
        failure: Exception,
    ) = FailedMessage(bus, mailbox, message, failure)

    private inline fun <reified T> testMailbox(
        messages: MutableList<T> = mutableListOf(),
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

    fun <U : T?> deliveredInOrder(vararg delivered: U) = apply {
        assertThat(this.delivered).isEqualTo(delivered.toList())
    }

    fun noneReturned() = apply {
        assertThat(returned).isEmpty()
    }

    fun returnedInOrder(vararg returned: ReturnedMessage<*>) = apply {
        assertThat(this.returned).isEqualTo(returned.toList())
    }

    fun noneFailed() = apply {
        assertThat(failed).isEmpty()
    }

    fun failedInOrder(vararg failed: FailedMessage<*>) = apply {
        assertThat(this.failed).isEqualTo(failed.toList())
    }
}
