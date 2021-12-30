package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.CoderMalfunctionError
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    private val bus = TestMagicBus()

    @Test
    fun `should have distinct named mailboxes even with the same name`() {
        assertThat(namedMailbox<RightType>("BOB") {})
            .isNotEqualTo(namedMailbox<RightType>("BOB") {})
    }

    @Test
    fun `should do nothing when unsubscribing`() {
        val mailbox = testMailbox<RightType>()
            .subscribeTo(bus)
            .unsubscribeFrom(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noMessageDelivered()
            .returnedInOrder(message)
            .noFailingMailbox()
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
        val mailbox = testMailbox<RightType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noMessageReturned()
            .noFailingMailbox()
    }

    @Test
    fun `should deliver correctly to disparate subscribers`() {
        val mailboxForDelivery = testMailbox<RightType>().subscribeTo(bus)
        val mailboxForNoDelivery = testMailbox<LeftType>().subscribeTo(bus)
        val mailboxForAliens = testMailbox<AlienType>().subscribeTo(bus)

        assertThat(bus.subscribersTo<RightType>())
            .containsOnly(mailboxForDelivery)

        val message = RightType()
        val alienMessage = AlienType()

        bus.post(message)
        bus.post(alienMessage)

        assertOn(mailboxForDelivery)
            .deliveredInOrder(message)
            .noMessageReturned()
            .noFailingMailbox()
        assertOn(mailboxForNoDelivery)
            .noMessageDelivered()
            .noMessageReturned()
            .noFailingMailbox()
        assertOn(mailboxForAliens)
            .deliveredInOrder(alienMessage)
            .noMessageReturned()
            .noFailingMailbox()
    }

    @Test
    fun `should not receive wrong type`() {
        val mailbox = testMailbox<LeftType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noMessageDelivered()
            .returnedInOrder(message)
            .noFailingMailbox()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = testMailbox<BaseType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noMessageReturned()
            .noFailingMailbox()
    }

    @Test
    fun `should save dead letters`() {
        val message = LeftType()

        bus.post(message)

        assertOn(allMailboxes())
            .noMessageDelivered()
            .returnedInOrder(message)
            .noFailingMailbox()
    }

    @Test
    fun `should provide distinct discard mailboxes`() {
        assertThat(discard<RightType>()).isNotEqualTo(discard<RightType>())
    }

    @Test
    fun `should provide distinct failure mailboxes`() {
        assertThat(failWith<RightType> { Exception() })
            .isNotEqualTo(failWith<RightType> { Exception() })
    }

    @Test
    fun `should post failed messages separately for each mailbox`() {
        val failureA = Exception("A")
        val brokenMailboxA = failWith<LeftType> { failureA }.subscribeTo(bus)
        val failureB = Exception("B")
        val brokenMailboxB = failWith<LeftType> { failureB }.subscribeTo(bus)
        val message = LeftType()

        bus.post(message)

        assertOn(allMailboxes())
            .noMessageDelivered()
            .noMessageReturned()
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
            bus.post(LeftType())
        }.isSameAs(failure)
    }

    @Test
    fun `should let JVM errors (Error vs Exception) bubble out`() {
        val failure = CoderMalfunctionError(
            Exception("SOMETHING WICKED THIS WAY COMES")
        )
        bus += failWith<LeftType> { failure }

        assertThatThrownBy {
            bus.post(LeftType())
        }.isSameAs(failure)
    }

    @Test
    fun `should post other exceptions and receive them`() {
        val failure = Exception()
        val badMailbox = failWith<RightType> { failure }.subscribeTo(bus)
        val allMailbox = testMailbox<Any>().subscribeTo(bus)

        val message = RightType()
        val failedMessage = FailedMessage(bus, badMailbox, message, failure)

        bus.post(message)

        assertOn(allMailbox)
            .noMessageReturned()
            .deliveredInOrder(message, failedMessage)
            .failedInOrder(badMailbox with message and failure)
    }

    @Test
    fun `should be fatal for a failure mailbox to itself fail`() {
        bus += failWith<RightType> { Exception() }
        bus += failWith<FailedMessage<Any>> { Exception() }

        assertThrows<StackOverflowError> {
            bus.post(RightType())
        }
    }

    @Test
    fun `should receive mailboxes for same type in subscription order`() {
        val mailboxes = OrderedMailboxes()
        val mailboxA = mailboxes.orderedMailbox<RightType>().subscribeTo(bus)
        val mailboxB = mailboxes.orderedMailbox<RightType>().subscribeTo(bus)

        bus.post(RightType())

        assertThat(mailboxes.deliveriesInOrder()).isEqualTo(
            listOf(
                mailboxA,
                mailboxB,
            )
        )
    }

    @Test
    fun `should receive parent types before child types`() {
        // FYI -- it is important that mailboxes subscribe *not* in class
        // hierarchy order, so that the test can verify messages are received
        // in the correct order nonetheless
        val mailboxes = OrderedMailboxes()
        val rightMailbox =
            mailboxes.orderedMailbox<RightType>().subscribeTo(bus)
        //  And an unrelated type which should *not* show up
        mailboxes.orderedMailbox<LeftType>().subscribeTo(bus)
        val farRightMailbox =
            mailboxes.orderedMailbox<FarRightType>().subscribeTo(bus)
        val baseMailbox =
            mailboxes.orderedMailbox<BaseType>().subscribeTo(bus)
        val allMailbox =
            mailboxes.orderedMailbox<Any>().subscribeTo(bus)
        //  And another unrelated type which should *not* show up
        mailboxes.orderedMailbox<LeftType>().subscribeTo(bus)

        bus.post(FarRightType())

        assertThat(mailboxes.deliveriesInOrder()).isEqualTo(
            listOf(
                allMailbox,
                baseMailbox,
                rightMailbox,
                farRightMailbox
            )
        )
    }

    @Test
    fun `should unsubscribe exact type`() {
        val mailbox = discard<LeftType>().subscribeTo(bus)

        mailbox.unsubscribeFrom(bus)

        assertThat(bus.subscribersTo<LeftType>()).isEmpty()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxForDelivery = discard<RightType>().subscribeTo(bus)
        val mailboxForNoDelivery = discard<RightType>().subscribeTo(bus)

        bus -= mailboxForNoDelivery

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
    fun `should fail to unsubscribe exact mailbox regardless of other mailboxes`() {
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
        val mailboxRight = testMailbox<RightType>().subscribeTo(bus)
        val mailboxBase = testMailbox<BaseType>().subscribeTo(bus)

        assertThat(bus.subscribersTo<RightType>())
            .containsOnly(mailboxBase, mailboxRight)
    }

    @Test
    fun `should provide subscribers for message type in subscribed-order`() {
        val mailboxes = (1..10).map {
            // An interesting example of when the return type is needed:
            // 1) Kotlin returns the final value of the lambda
            // 2) `bus += mailbox` return Unit
            // 3) `subscribeTo` returns the mailbox, fluent style
            // 4) The test wants to compare mailboxes, so Unit is wrong
            testMailbox<RightType>().subscribeTo(bus)
        }

        // It is unusual to test the test code, but the production test relies
        // critically on test mailbox instances being non-equal
        assertThat(testMailbox<RightType>())
            .isNotEqualTo(testMailbox<RightType>())
        assertThat(bus.subscribersTo<RightType>()).isEqualTo(mailboxes)
    }

    @Test
    fun `should provide accurate details on dead letters`() {
        val message = RightType()

        bus.post(message)

        assertThat(bus.returned).containsOnly(ReturnedMessage(bus, message))
    }

    @Test
    fun `should have default rejected letter box`() {
        val defaultMailboxCountForReturnedAndFailedMessages = 2

        assertThat(bus.subscribersTo<FailedMessage<Any>>())
            .hasSize(defaultMailboxCountForReturnedAndFailedMessages)
    }

    @Test
    fun `should deliver to first subscriber for returned messages before default`() {
        val subscribers = bus.subscribersTo<ReturnedMessage<Any>>()

        assertThat(subscribers.first().toString())
            .isEqualTo("DISCARD-MAILBOX<ReturnedMessage>")
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

        bus.post(RightType())

        assertOn(allMailboxes())
            .noMessageDelivered()
            .noMessageReturned()
            .noFailingMailbox()
    }

    @Test
    fun `should provide accurate details on failed posts`() {
        val failure = Exception()
        val mailbox = failWith<RightType> { failure }.subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertThat(bus.failed)
            .containsOnly(FailedMessage(bus, mailbox, message, failure))
    }

    @Test
    fun `should have named mailboxes`() {
        val name = "BOB'S YER UNKEL"
        val mailbox = namedMailbox<RightType>(name) {}

        assertThat(mailbox.toString()).isEqualTo(name)
    }

    private fun <T> assertOn(vararg delivered: TestMailbox<T>) =
        assertOn(delivered.asList())

    private fun <T> assertOn(delivered: List<TestMailbox<T>>) =
        AssertDelivered(
            delivered.flatMap { it.messages },
            bus.returned,
            bus.failed
        )

    private infix fun <T> Mailbox<T>.with(message: T) = this to message
    private infix fun <T> Pair<Mailbox<T>, T>.and(failure: Exception) =
        this to failure

    private inline fun <reified T> testMailbox(
        messages: MutableList<T> = mutableListOf(),
    ) = TestMailbox(bus, T::class.java, messages)

    private fun allMailboxes() = listOf<TestMailbox<Any>>()

    private inner class AssertDelivered<T>(
        private val delivered: List<T>,
        private val returned: List<ReturnedMessage<*>>,
        private val failed: List<FailedMessage<*>>,
    ) {
        fun noMessageDelivered() = apply {
            assertThat(delivered).isEmpty()
        }

        fun <U : T> deliveredInOrder(vararg delivered: U) = apply {
            assertThat(this.delivered).isEqualTo(delivered.toList())
        }

        fun noMessageReturned() = apply {
            assertThat(returned).isEmpty()
        }

        fun returnedInOrder(vararg returned: Any) = apply {
            assertThat(this.returned).isEqualTo(
                returned.map {
                    ReturnedMessage(bus, it)
                }
            )
        }

        fun noFailingMailbox() = apply {
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

private class OrderedMailboxes {
    private val sequence = AtomicInteger(-1)
    private val mailboxes = mutableListOf<OrderedMailbox<*>>()

    inline fun <reified T> orderedMailbox(): Mailbox<T> =
        OrderedMailbox(T::class.java).also { mailboxes.add(it) }

    fun deliveriesInOrder(): List<Mailbox<*>> = mailboxes
        .filter { -1 < it.order } // -1 means no deliveries
        .sortedBy {
            it.order
        }

    private inner class OrderedMailbox<T>(
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
 * @todo Why is returned `<Any>` but failed is `<*>`?
 */
private class TestMagicBus : SimpleMagicBus() {
    val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()
    private val _returned = mutableListOf<ReturnedMessage<Any>>()
    private val _failed = mutableListOf<FailedMessage<*>>()

    val returned: List<ReturnedMessage<Any>> get() = _returned
    val failed: List<FailedMessage<*>> get() = _failed

    init {
        this += namedMailbox<ReturnedMessage<Any>>("TEST-DEAD-LETTERBOX") {
            _returned += it
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
