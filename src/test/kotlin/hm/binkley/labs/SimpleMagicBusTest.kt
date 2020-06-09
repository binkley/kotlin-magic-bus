package hm.binkley.labs

import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SimpleMagicBusTest {
    private val returned: MutableList<ReturnedMessage> =
        CopyOnWriteArrayList()
    private val failed: MutableList<FailedMessage> = CopyOnWriteArrayList()
    private val observed: MutableMap<Mailbox<*>, MutableList<Any>> =
        mutableMapOf()

    private lateinit var bus: MagicBus

    @BeforeEach
    fun setUp() {
        bus = SimpleMagicBus.of(
            { message -> returned.add(message) },
            { message -> failed.add(message) }
        ) { mailbox, message ->
            observed
                .computeIfAbsent(mailbox) { mutableListOf() }
                .add(message)
        }
    }

    @Test
    fun shouldReceiveCorrectType() {
        val mailbox = TestMailbox<RightType>()
        bus.subscribe(RightType::class.java, mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun shouldNotReceiveWrongType() {
        val mailbox = TestMailbox<LeftType>()
        bus.subscribe(LeftType::class.java, mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(this.with(message))
            .noneFailed()
    }

    @Test
    fun shouldReceiveSubtypes() {
        val mailbox = TestMailbox<BaseType>()
        bus.subscribe(BaseType::class.java, mailbox)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun shouldSaveDeadLetters() {
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .returned(with(message))
            .noneFailed()
    }

    @Test
    fun shouldSaveFailedPosts() {
        val failure = Exception()
        val mailbox: Mailbox<LeftType> = failWith { failure }
        bus.subscribe(LeftType::class.java, mailbox)
        val message = LeftType()

        bus.post(message)

        assertOn(noMailbox<Any>())
            .noneDelivered()
            .noneReturned()
            .failed(with(mailbox, message, failure))
    }

    @Test
    fun shouldThrowFailedPostsForUnchecked() {
        assertThatThrownBy {
            bus.subscribe(
                LeftType::class.java, failWith {
                    RuntimeException()
                }
            )
            bus.post(LeftType())
        }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun shouldReceiveEarlierSubscribersFirst() {
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
    fun shouldReceiveOnParentTypeFirstInParentFirstOrder() {
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
    fun shouldUnsubscribeOnlyMailbox() {
        val mailbox = TestMailbox<LeftType>()
        bus.subscribe(LeftType::class.java, mailbox)
        bus.unsubscribe(LeftType::class.java, mailbox)
        val message = LeftType()

        bus.post(message)

        assertOn(mailbox)
            .noneDelivered()
            .returned(this.with(message))
            .noneFailed()
    }

    @Test
    fun shouldUnsubscribeRightMailbox() {
        val mailboxA = TestMailbox<RightType>()
        val mailboxB: Mailbox<RightType> = failWith { Exception() }
        bus.subscribe(RightType::class.java, mailboxA)
        bus.subscribe(RightType::class.java, mailboxB)
        bus.unsubscribe(RightType::class.java, mailboxB)
        val message = RightType()

        bus.post(message)

        assertOn(mailboxA)
            .delivered(message)
            .noneReturned()
            .noneFailed()
    }

    @Test
    fun shouldUnsubscribeThreadSafely() {
        await().atMost(2000L, MILLISECONDS).until {
            val latch = CountDownLatch(100)
            IntStream.range(0, 100).parallel()
                .forEach { _: Int ->
                    val mailbox: Mailbox<RightType> = Discard()
                    bus.subscribe(RightType::class.java, mailbox)
                    bus.unsubscribe(RightType::class.java, mailbox)
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
                .returned(this.with(message))
                .noneFailed()
            true
        }
    }

    @Test
    fun shouldComplainWhenUnsubscribingBadMailbox() {
        assertThatThrownBy {
            bus.subscribe(RightType::class.java, Discard())
            val mailbox = Discard<RightType>()
            bus.unsubscribe(RightType::class.java, mailbox)
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun shouldComplainWhenUnsubscribingBadMessageType() {
        assertThatThrownBy {
            val mailbox = Discard<RightType>()
            bus.unsubscribe(RightType::class.java, mailbox)
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun shouldProvideSubscriberForMessageType() {
        val a: Mailbox<RightType> = object :
            Mailbox<RightType> {
            override operator fun invoke(message: RightType) {}
            override fun toString(): String {
                return "b"
            }
        }
        val b: Mailbox<BaseType> = object :
            Mailbox<BaseType> {
            override operator fun invoke(message: BaseType) {}
            override fun toString(): String {
                return "a"
            }
        }
        bus.subscribe(BaseType::class.java, b)
        bus.subscribe(RightType::class.java, a)

        assertThat((bus as SimpleMagicBus).subscribers(RightType::class.java))
            .isEqualTo(listOf(b, a))
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

internal class Discard<T> : Mailbox<T> {
    override operator fun invoke(message: T) {}
}

internal fun <T, E : Exception> failWith(ctor: () -> E): Mailbox<T> =
    { throw ctor() }

internal abstract class BaseType
internal class LeftType : BaseType()
internal open class RightType : BaseType()
internal class FarRightType : RightType()
