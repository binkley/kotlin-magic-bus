package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.CoderMalfunctionError
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    private val bus = TestMagicBus()

    @Test
    fun `should have distinct named mailboxen even with the same name`() {
        assertThat(namedMailbox<RightType>("BOB") {})
            .isNotEqualTo(namedMailbox<RightType>("BOB") {})
    }

    @Test
    fun `should do nothing when unsubscribing`() {
        val mailbox = testMailbox<RightType>()
            .subscribeTo(bus)
            .unsubscribeFrom(bus)
        val message = RightType()

        message postTo bus

        assertOn(mailbox)
            .noDeliveredMessages()
            .undeliveredInOrder(message)
            .noFailedMessages()
    }

    @Test
    fun `should stop tracking message types with no subscribers`() {
        testMailbox<RightType>()
            .subscribeTo(bus)
            .unsubscribeFrom(bus)

        assertThat(bus.subscriptions.containsKey(RightType::class.java))
            .isFalse
    }

    @Test
    fun `should receive correct type`() {
        val mailbox = testMailbox<RightType>() subscribeTo bus
        val message = RightType()

        message postTo bus

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noUndeliveredMessages()
            .noFailedMessages()
    }

    @Test
    fun `should deliver correctly to disparate subscribers`() {
        val mailboxForDelivery = testMailbox<RightType>() subscribeTo bus
        val mailboxForUndelivered = testMailbox<LeftType>() subscribeTo bus
        val mailboxForAliens = testMailbox<AlienType>() subscribeTo bus

        assertThat(bus.subscribersTo<RightType>())
            .containsOnly(mailboxForDelivery)

        val message = RightType()
        val alienMessage = AlienType()

        message postTo bus
        alienMessage postTo bus

        assertOn(mailboxForDelivery)
            .deliveredInOrder(message)
            .noUndeliveredMessages()
            .noFailedMessages()
        assertOn(mailboxForUndelivered)
            .noDeliveredMessages()
            .noUndeliveredMessages()
            .noFailedMessages()
        assertOn(mailboxForAliens)
            .deliveredInOrder(alienMessage)
            .noUndeliveredMessages()
            .noFailedMessages()
    }

    @Test
    fun `should not receive wrong type`() {
        val mailbox = testMailbox<LeftType>() subscribeTo bus
        val message = RightType()

        message postTo bus

        assertOn(mailbox)
            .noDeliveredMessages()
            .undeliveredInOrder(message)
            .noFailedMessages()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = testMailbox<BaseType>() subscribeTo bus
        val message = RightType()

        message postTo bus

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noUndeliveredMessages()
            .noFailedMessages()
    }

    @Test
    fun `should save dead letters`() {
        val message = LeftType()

        message postTo bus

        assertOn(allMailboxen())
            .noDeliveredMessages()
            .undeliveredInOrder(message)
            .noFailedMessages()
    }

    @Test
    fun `should provide distinct discard mailboxen`() {
        assertThat(discard<RightType>()).isNotEqualTo(discard<RightType>())
    }

    @Test
    fun `should provide distinct failure mailboxen`() {
        assertThat(failWith<RightType> { Exception() })
            .isNotEqualTo(failWith<RightType> { Exception() })
    }

    @Test
    fun `should post failed messages separately for each mailbox`() {
        val failureA = Exception("A")
        val brokenMailboxA = failWith<LeftType> { failureA } subscribeTo bus
        val failureB = Exception("B")
        val brokenMailboxB = failWith<LeftType> { failureB } subscribeTo bus
        val message = LeftType()

        message postTo bus

        assertOn(allMailboxen())
            .noDeliveredMessages()
            .noUndeliveredMessages()
            .failedInOrder(
                brokenMailboxA with message and failureA,
                brokenMailboxB with message and failureB,
            )
    }

    @Test
    fun `should bubble out runtime exceptions`() {
        val failure = RuntimeException()
        bus += failWith<LeftType> { failure }

        assertThatThrownBy {
            LeftType() postTo bus
        }.isSameAs(failure)
    }

    @Test
    fun `should let JVM errors (Error vs Exception) bubble out`() {
        val failure = CoderMalfunctionError(
            Exception("SOMETHING WICKED THIS WAY COMES")
        )
        bus += failWith<LeftType> { failure }

        assertThatThrownBy {
            LeftType() postTo bus
        }.isSameAs(failure)
    }

    @Test
    fun `should post other exceptions and receive them`() {
        val failure = Exception()
        val badMailbox = failWith<RightType> { failure } subscribeTo bus
        val allMailbox = testMailbox<Any>() subscribeTo bus

        val message = RightType()
        val failedMessage = FailedMessage(bus, badMailbox, message, failure)
        val returnReceipt = ReturnReceipt(bus, message)

        message postTo bus

        assertOn(allMailbox)
            .noUndeliveredMessages()
            .deliveredInOrder(message, failedMessage, returnReceipt)
            .failedInOrder(badMailbox with message and failure)
    }

    @Test
    fun `should be fatal for a failure mailbox to itself fail`() {
        bus += failWith<RightType> { Exception() }
        bus += failWith<FailedMessage<Any>> { Exception() }

        // TODO: Rather than overflowing the stack, how to alert caller?
        //       Not appropriate to add a logging framework, so what else?
        assertThrows<StackOverflowError> {
            RightType() postTo bus
        }
    }

    @Test
    fun `should be fatal for a mailbox to repost in an infinite cycle`() {
        bus += namedMailbox<RightType>("REPOSTING-RIGHT") {
            it postTo bus
        }

        // TODO: Rather than overflowing the stack, how to alert caller?
        //       Not appropriate to add a logging framework, so what else?
        assertThrows<StackOverflowError> {
            RightType() postTo bus
        }
    }

    @Test
    fun `should receive mailboxen for same type in subscription order`() {
        val mailboxen = OrderedMailboxen()
        val mailboxA = mailboxen.orderedMailbox<RightType>() subscribeTo bus
        val mailboxB = mailboxen.orderedMailbox<RightType>() subscribeTo bus

        RightType() postTo bus

        assertThat(mailboxen.deliveriesInOrder()).isEqualTo(
            listOf(
                mailboxA,
                mailboxB,
            )
        )
    }

    @Test
    fun `should receive parent types before child types`() {
        // FYI -- it is important that mailboxen subscribe *not* in class
        // hierarchy order, so that the test can verify messages are received
        // in the correct order nonetheless
        val mailboxen = OrderedMailboxen()
        val rightMailbox =
            mailboxen.orderedMailbox<RightType>() subscribeTo bus
        //  Add an unrelated type which should *not* show up
        mailboxen.orderedMailbox<LeftType>() subscribeTo bus
        val farRightMailbox =
            mailboxen.orderedMailbox<FarRightType>() subscribeTo bus
        val receiptsMailbox =
            mailboxen.orderedMailbox<ReturnReceipt<*>>() subscribeTo bus
        val baseMailbox =
            mailboxen.orderedMailbox<BaseType>() subscribeTo bus
        val allMailbox =
            mailboxen.orderedMailbox<Any>() subscribeTo bus
        //  And another unrelated type which should *not* show up
        mailboxen.orderedMailbox<LeftType>() subscribeTo bus

        FarRightType() postTo bus

        assertThat(mailboxen.deliveriesInOrder()).isEqualTo(listOf(
            allMailbox,
            baseMailbox,
            rightMailbox,
            farRightMailbox,
            receiptsMailbox,
        ))
    }

    @Test
    fun `should unsubscribe exact type`() {
        val mailbox = discard<LeftType>() subscribeTo bus

        mailbox unsubscribeFrom bus

        assertThat(bus.subscribersTo<LeftType>()).isEmpty()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxForDelivery = discard<RightType>() subscribeTo bus
        val mailboxForUndelivered = discard<RightType>() subscribeTo bus

        bus -= mailboxForUndelivered

        assertThat(bus.subscribersTo<RightType>())
            .containsOnly(mailboxForDelivery)
    }

    @Test
    fun `should fail to unsubscribe for message type`() {
        val mailbox = discard<RightType>()

        assertThrows<NoSuchElementException> {
            bus -= mailbox
        }
    }

    @Test
    fun `should fail to unsubscribe exact mailbox regardless of other mailboxen`() {
        bus += discard<RightType>()
        val mailboxNotSubscribed = discard<RightType>()

        assertThrows<NoSuchElementException> {
            bus -= mailboxNotSubscribed
        }
    }

    @Test
    fun `should provide subscribers for message type in base-up order`() {
        // Subscription in derived-base order to show that the subscriber
        // list is in base-derived order
        val mailboxRight = testMailbox<RightType>() subscribeTo bus
        val mailboxBase = testMailbox<BaseType>() subscribeTo bus

        assertThat(bus.subscribersTo<RightType>())
            .containsOnly(mailboxBase, mailboxRight)
    }

    @Test
    fun `should provide subscribers for message type in subscribed-order`() {
        val mailboxen = (1..10).map {
            // An interesting example of when the return type is needed:
            // 1) Kotlin returns the final value of the lambda
            // 2) `bus += mailbox` return Unit
            // 3) `subscribeTo` returns the mailbox, fluent style
            // 4) The test wants to compare mailboxen, so Unit is wrong
            testMailbox<RightType>() subscribeTo bus
        }

        // It is unusual to test the test code, but the production test relies
        // critically on test mailbox instances being non-equal
        assertThat(testMailbox<RightType>())
            .isNotEqualTo(testMailbox<RightType>())
        assertThat(bus.subscribersTo<RightType>()).isEqualTo(mailboxen)
    }

    @Test
    fun `should provide accurate details on dead letters`() {
        val message = RightType()

        message postTo bus

        assertThat(bus.undelivered).containsOnly(
            UndeliveredMessage(
                bus,
                message
            )
        )
    }

    @Test
    fun `should have default rejected letter box`() {
        val defaultMailboxCountForUndeliveredAndFailedMessages = 2

        assertThat(bus.subscribersTo<FailedMessage<Any>>())
            .hasSize(defaultMailboxCountForUndeliveredAndFailedMessages)
    }

    @Test
    fun `should deliver to first subscriber for undelivered messages before default`() {
        val subscribers = bus.subscribersTo<UndeliveredMessage<Any>>()

        assertThat(subscribers.first().toString())
            .isEqualTo("DISCARD-MAILBOX<UndeliveredMessage>")
    }

    @Test
    fun `should deliver to first subscriber for failed messages before default`() {
        val subscribers = bus.subscribersTo<FailedMessage<Any>>()

        assertThat(subscribers.first().toString())
            .isEqualTo("DISCARD-MAILBOX<FailedMessage>")
    }

    @Test
    fun `should discard in the discard letter box`() {
        bus += discard<RightType>()

        RightType() postTo bus

        assertOn(allMailboxen())
            .noDeliveredMessages()
            .noUndeliveredMessages()
            .noFailedMessages()
    }

    @Test
    fun `should provide accurate details on failed posts`() {
        val failure = Exception()
        val mailbox = failWith<RightType> { failure } subscribeTo bus
        val message = RightType()

        message postTo bus

        assertThat(bus.failed)
            .containsOnly(FailedMessage(bus, mailbox, message, failure))
    }

    @Test
    fun `should have named mailboxen`() {
        val name = "BOB'S YER UNKEL"
        val mailbox = namedMailbox<RightType>(name) {}

        assertThat(mailbox.toString()).isEqualTo(name)
    }

    private fun <T> assertOn(vararg delivered: TestMailbox<T>) =
        assertOn(delivered.asList())

    private fun <T> assertOn(delivered: List<TestMailbox<T>>) =
        AssertDelivered(
            delivered.flatMap { it.messages },
            bus.undelivered,
            bus.failed
        )

    private infix fun <T> Mailbox<T>.with(message: T) = this to message
    private infix fun <T> Pair<Mailbox<T>, T>.and(failure: Exception) =
        this to failure

    private inline fun <reified T> testMailbox(
        messages: MutableList<T> = mutableListOf(),
    ) = TestMailbox(bus, T::class.java, messages)

    private fun allMailboxen() = listOf<TestMailbox<Any>>()

    private inner class AssertDelivered<T>(
        private val delivered: List<T>,
        private val undelivered: List<UndeliveredMessage<*>>,
        private val failed: List<FailedMessage<*>>,
    ) {
        fun noDeliveredMessages() = apply {
            assertThat(delivered).isEmpty()
        }

        fun <U : T> deliveredInOrder(vararg delivered: U) = apply {
            assertThat(this.delivered).isEqualTo(delivered.toList())
        }

        fun noUndeliveredMessages() = apply {
            assertThat(undelivered).isEmpty()
        }

        fun undeliveredInOrder(vararg undelivered: Any) = apply {
            assertThat(this.undelivered).isEqualTo(
                undelivered.map {
                    UndeliveredMessage(bus, it)
                }
            )
        }

        fun noFailedMessages() = apply {
            assertThat(failed).isEmpty()
        }

        fun <U : T> failedInOrder(
            vararg failed: Pair<Pair<Mailbox<U>, U>, Exception>,
        ) = apply {
            assertThat(this.failed).isEqualTo(
                failed.map {
                    val (first, failure) = it
                    val (mailbox, message) = first
                    FailedMessage(bus, mailbox, message, failure)
                }
            )
        }
    }
}

private class OrderedMailboxen {
    private val sequence = AtomicInteger(-1)
    private val mailboxen = mutableListOf<OrderedMailbox<*>>()

    inline fun <reified T> orderedMailbox(): OrderedMailbox<T> =
        OrderedMailbox(T::class.java).also { mailboxen.add(it) }

    fun deliveriesInOrder(): List<Mailbox<*>> = mailboxen
        .filter { -1 < it.order } // -1 means no deliveries
        .sortedBy {
            it.order
        }.also {
            println("Mailboxen order: $it")
        }

    inner class OrderedMailbox<T>(
        private val messageType: Class<T>,
    ) : Mailbox<T> {
        private val myOrder: AtomicInteger = AtomicInteger(-1)
        val order: Int get() = myOrder.get()

        override fun invoke(message: T) =
            myOrder.set(sequence.incrementAndGet())

        override fun toString() =
            "TEST-ORDERED-MAILBOX<${messageType.simpleName}@$myOrder>"
    }
}

private fun <T : Any> failWith(failure: () -> Throwable): Mailbox<T> =
    { throw failure() }

private abstract class BaseType
private class LeftType : BaseType()
private open class RightType : BaseType()
private class FarRightType : RightType()
private class AlienType

/**
 * @todo Why is delivered key `<*>` but the value is `<Any>`?
 * @todo Why is undelivered `<Any>` but failed is `<*>`?
 */
private class TestMagicBus : SimpleMagicBus() {
    val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()
    private val _undelivered = mutableListOf<UndeliveredMessage<Any>>()
    private val _failed = mutableListOf<FailedMessage<*>>()

    val undelivered: List<UndeliveredMessage<Any>> get() = _undelivered
    val failed: List<FailedMessage<*>> get() = _failed

    init {
        this += namedMailbox<UndeliveredMessage<Any>>("TEST-DEAD-LETTERBOX") {
            _undelivered += it
        }
        this += namedMailbox<FailedMessage<Any>>("TEST-FAILED-LETTERBOX") {
            _failed += it
        }
    }
}

private class TestMailbox<T>(
    private val bus: TestMagicBus,
    private val messageType: Class<T>,
    val messages: MutableList<T>,
) : Mailbox<T> {
    override operator fun invoke(message: T) {
        messages.add(message)
        bus.delivered.getOrPut(this) {
            mutableListOf()
        } += message as Any
    }

    override fun toString() = "TEST-MAILBOX<${messageType.simpleName}>"
}
