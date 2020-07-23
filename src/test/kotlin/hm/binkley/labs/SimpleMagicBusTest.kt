package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

internal class SimpleMagicBusTest {
    private val returned: MutableList<ReturnedMessage> =
        CopyOnWriteArrayList()
    private val failed: MutableList<FailedMessage> = CopyOnWriteArrayList()
    private val observed: MutableMap<Mailbox<*>, MutableList<Any>> =
        mutableMapOf()

    private val bus: MagicBus = SimpleMagicBus.of(
        returned = { message -> returned.add(message) },
        failed = { message -> failed.add(message) },
        observed = { mailbox, message ->
            observed
                .computeIfAbsent(mailbox) { mutableListOf() }
                .add(message)
        }
    )

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
            bus.subscribe(
                LeftType::class.java,
                failWith { RuntimeException() }
            )
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
        bus.subscribe(RightType::class.java, record(delivery, first))
        bus.subscribe(RightType::class.java, record(delivery, second))
        bus.subscribe(RightType::class.java, record(delivery, third))
        bus.subscribe(RightType::class.java, record(delivery, fourth))
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
        bus.subscribe(RightType::class.java, record(delivery, right))
        bus.subscribe(FarRightType::class.java, record(delivery, farRight))
        bus.subscribe(Any::class.java, record(delivery, anythingElse))
        bus.subscribe(BaseType::class.java, record(delivery, base))
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
    fun `should unsubscribe for exact type`() {
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
    fun `should provide subscriber for message type`() {
        val mailboxRight: Mailbox<RightType> = object : Mailbox<RightType> {
            override operator fun invoke(message: RightType) {}
            override fun toString() = "right-type mailbox"
        }
        val mailboxBase: Mailbox<BaseType> = object : Mailbox<BaseType> {
            override operator fun invoke(message: BaseType) {}
            override fun toString() = "base-type mailbox"
        }
        mailboxBase.deliverFrom(bus)
        mailboxRight.deliverFrom(bus)

        assertThat((bus as SimpleMagicBus).subscribers(RightType::class.java))
            .isEqualTo(listOf(mailboxBase, mailboxRight))
    }

    @Test
    fun `should provide accurate details on dead letters`() {
        val message = this
        bus.post(message)
        val dead = returned[0]
        assertThat(dead.bus).isSameAs(bus)
        assertThat(dead.message).isSameAs(message)
    }

    @Test
    fun `should provide accurate details on failed posts`() {
        val reason = Exception()
        val mailbox: Mailbox<SimpleMagicBusTest> = failWith { reason }
        mailbox.deliverFrom(bus)
        val message = this
        bus.post(message)
        val failed = failed[0]
        assertThat(failed.bus).isSameAs(bus)
        assertThat(failed.mailbox).isSameAs(mailbox)
        assertThat(failed.message).isSameAs(message)
        assertThat(failed.failure).isSameAs(reason)
    }

    private fun <T> assertOn(delivered: List<T>): AssertDelivery<T> {
        return AssertDelivery(delivered)
    }

    private fun <T> assertOn(delivered: TestMailbox<T>): AssertDelivery<T> {
        return assertOn(delivered.messages)
    }

    private fun with(message: Any): ReturnedMessage {
        return ReturnedMessage(bus, message)
    }

    private fun with(
        mailbox: Mailbox<*>,
        message: Any,
        failure: Exception
    ): FailedMessage {
        return FailedMessage(bus, mailbox, message, failure)
    }

    internal inner class AssertDelivery<T>(private val delivered: List<T>) {
        internal fun noneDelivered(): AssertDelivery<T> {
            assertThat(delivered).isEmpty()
            return this
        }

        @SafeVarargs
        internal fun <U : T?> delivered(
            vararg delivered: U
        ): AssertDelivery<T> {
            assertThat(this.delivered).containsExactly(*delivered)
            return this
        }

        internal fun noneReturned(): AssertDelivery<T> {
            assertThat(returned).isEmpty()
            return this
        }

        internal fun returned(
            vararg returned: ReturnedMessage
        ): AssertDelivery<T> {
            assertThat(this@SimpleMagicBusTest.returned)
                .containsExactly(*returned)
            return this
        }

        internal fun noneFailed(): AssertDelivery<T> {
            assertThat(failed).isEmpty()
            return this
        }

        internal fun failed(vararg failed: FailedMessage): AssertDelivery<T> {
            assertThat(this@SimpleMagicBusTest.failed)
                .containsExactly(*failed)
            return this
        }
    }

    companion object {
        private fun <T> noMailbox(): List<T> {
            return emptyList()
        }

        private fun <T> record(
            order: AtomicInteger,
            record: AtomicInteger
        ): Mailbox<T> = { record.set(order.getAndIncrement()) }
    }
}

internal class TestMailbox<T>(
    val messages: MutableList<T> = ArrayList(1)
) : Mailbox<T> {
    override operator fun invoke(message: T) {
        messages.add(message)
    }
}

internal abstract class BaseType
internal class LeftType : BaseType()
internal open class RightType : BaseType()
internal class FarRightType : RightType()
