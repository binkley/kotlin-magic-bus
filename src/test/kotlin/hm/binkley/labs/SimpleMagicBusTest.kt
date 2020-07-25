package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

internal class SimpleMagicBusTest {
    private val returned = CopyOnWriteArrayList<ReturnedMessage>()
    private val failed = CopyOnWriteArrayList<FailedMessage>()
    private val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()

    private val bus = SimpleMagicBus.onReturn {
        returned.add(it)
    } onFailure {
        failed.add(it)
    } onDelivery { mailbox, message ->
        delivered
            .computeIfAbsent(mailbox) { mutableListOf() }
            .add(message)
    }

    @Test
    fun `should receive correct type`() {
        val mailbox = TestMailbox<RightType>()
        mailbox.deliverFrom(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should not receive wrong type`() {
        val mailbox = TestMailbox<LeftType>()
        mailbox.deliverFrom(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = TestMailbox<BaseType>()
        mailbox.deliverFrom(bus)
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
        mailbox.deliverFrom(bus)
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .noneReturned()
            .failed(with(mailbox, message, reason))
    }

    @Test
    fun `should throw failed posts for unchecked exceptions`() {
        assertThatThrownBy {
            val mailbox: Mailbox<LeftType> = failWith { RuntimeException() }
            mailbox.deliverFrom(bus)

            bus.post(LeftType())
        }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `should receive earlier subscribers first`() {
        val delivery = AtomicInteger()
        val first = AtomicInteger()
        val second = AtomicInteger()
        val third = AtomicInteger()
        val fourth = AtomicInteger()
        record<RightType>(delivery, first).deliverFrom(bus)
        record<RightType>(delivery, second).deliverFrom(bus)
        record<RightType>(delivery, third).deliverFrom(bus)
        record<RightType>(delivery, fourth).deliverFrom(bus)

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
        record<RightType>(delivery, right).deliverFrom(bus)
        record<FarRightType>(delivery, farRight).deliverFrom(bus)
        record<Any>(delivery, anythingElse).deliverFrom(bus)
        record<BaseType>(delivery, base).deliverFrom(bus)

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
        val mailbox = TestMailbox<LeftType>()
        mailbox.deliverFrom(bus)
        mailbox.noDeliveryFrom(bus)
        val message = LeftType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxA = TestMailbox<RightType>()
        val mailboxB: Mailbox<RightType> = failWith { Exception() }
        mailboxA.deliverFrom(bus)
        mailboxB.deliverFrom(bus)
        mailboxB.noDeliveryFrom(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailboxA)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun `should fail to unsubscribe exact mailbox`() {
        val mailboxA = TestMailbox<RightType>()
        val mailboxB: Mailbox<RightType> = failWith { Exception() }
        mailboxA.deliverFrom(bus)

        assertThrows<NoSuchElementException> {
            mailboxB.noDeliveryFrom(bus)
        }
    }

    @Test
    fun `should unsubscribe safely across threads`() {
        await().atMost(2000L, MILLISECONDS).until {
            val latch = CountDownLatch(100)
            IntStream.range(0, 100).parallel().forEach { _: Int ->
                val mailbox: Mailbox<RightType> = TestMailbox()
                mailbox.deliverFrom(bus)
                mailbox.noDeliveryFrom(bus)
                latch.countDown()
            }
            assertThat(
                latch.await(
                    1000L,
                    MILLISECONDS
                )
            ).isNotEqualTo(0)
            val message = RightType()

            bus.post(message)

            assertOn(noMailbox<Any>())
                .returned(with(message))
                .noneFailed()
            true
        }
    }

    @Test
    fun `should complain when unsubscribing from bad mailbox`() {
        assertThatThrownBy {
            discard<RightType>().noDeliveryFrom(bus)
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `should provide subscribers for message type in base-up order`() {
        // Subscription in child-parent order to show that the subscriber
        // list is in parent-child order
        val mailboxRight: Mailbox<RightType> = { }
        mailboxRight.deliverFrom(bus)
        val mailboxBase: Mailbox<BaseType> = { }
        mailboxBase.deliverFrom(bus)

        assertThat((bus as SimpleMagicBus).subscribers<RightType>())
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
    fun `should provide accurate details on failed posts`() {
        val reason = Exception()
        val mailbox: Mailbox<RightType> = failWith { reason }
        mailbox.deliverFrom(bus)
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
    private fun with(
        mailbox: Mailbox<*>,
        message: Any,
        failure: Exception
    ) = FailedMessage(bus, mailbox, message, failure)
}

private fun <T> noMailbox() = emptyList<T>()
private fun <T> record(
    order: AtomicInteger,
    record: AtomicInteger
): Mailbox<T> = { record.set(order.getAndIncrement()) }

private class TestMailbox<T>(
    val messages: MutableList<T> = ArrayList(1)
) : Mailbox<T> {
    override operator fun invoke(message: T) {
        messages.add(message)
    }
}

private abstract class BaseType
private class LeftType : BaseType()
private open class RightType : BaseType()
private class FarRightType : RightType()

private class AssertDelivery<T>(
    private val returned: List<ReturnedMessage>,
    private val failed: List<FailedMessage>,
    private val delivered: List<T>
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

    fun returned(vararg returned: ReturnedMessage) = apply {
        assertThat(this.returned).containsExactly(*returned)
    }

    fun noneFailed() = apply {
        assertThat(failed).isEmpty()
    }

    fun failed(vararg failed: FailedMessage) = apply {
        assertThat(this.failed).containsExactly(*failed)
    }
}
