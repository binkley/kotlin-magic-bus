package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.CoderMalfunctionError
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    // TODO: Why is the key `<*>` but the value is `<Any>`?
    private val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()

    // TODO: Why returned `<Any>` but failed is `<*>`?
    private val returned = mutableListOf<ReturnedMessage<Any>>()
    private val failed = mutableListOf<FailedMessage<*>>()

    // Do *not* use `DEFAULT_BUS`.  The test needs a new instance each run,
    // and `DEFAULT_BUS` is a global static
    private val bus = SimpleMagicBus().also { bus ->
        bus += namedMailbox<ReturnedMessage<Any>>("TEST-DEAD-LETTERBOX") {
            returned += it
        }
        bus += namedMailbox<FailedMessage<Any>>("TEST-FAILED-LETTERBOX") {
            failed += it
        }
    }

    @Test
    fun `should have distinct named mailboxes`() {
        assertThat(namedMailbox<RightType>("BOB") {})
            .isNotEqualTo(namedMailbox<RightType>("BOB") {})
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

        assertThat(bus.subscribers<RightType>())
            .containsOnly(mailboxForDelivery)

        val message = RightType()

        bus.post(message)

        assertOn(mailboxForDelivery)
            .deliveredInOrder(message)
            .noMessageReturned()
            .noFailingMailbox()
        assertOn(mailboxForNoDelivery)
            .noMessageDelivered()
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
            .returnedInOrder(with(message))
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
            .returnedInOrder(with(message))
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
                with(brokenMailboxA, message, failureA),
                with(brokenMailboxB, message, failureB)
            )
    }

    @Test
    fun `should bubble out runtime exceptions`() {
        val failure = RuntimeException()

        assertThatThrownBy {
            bus += failWith<LeftType> { failure }

            bus.post(LeftType())
        }.isSameAs(failure)
    }

    @Test
    fun `should bubble out JVM errors (Error vs Exception)`() {
        val failure = CoderMalfunctionError(Exception())
        assertThatThrownBy {
            bus += failWith<LeftType> { failure }

            bus.post(LeftType())
        }.isSameAs(failure)
    }

    @Test
    fun `should post other exceptions and receive them`() {
        val failure = Exception()
        val badMailbox = failWith<RightType> { failure }.subscribeTo(bus)
        val allMailbox = testMailbox<Any>().subscribeTo(bus)

        val message = RightType()
        val failedMessage = with(badMailbox, message, failure)

        bus.post(message)

        assertOn(allMailbox)
            .noMessageReturned()
            .deliveredInOrder(message, failedMessage)
            .failedInOrder(failedMessage)
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
        val ordering = AtomicInteger()
        val mailboxA = orderedMailbox<RightType>(ordering).subscribeTo(bus)
        val mailboxB = orderedMailbox<RightType>(ordering).subscribeTo(bus)

        bus.post(RightType())

        assertThat(mailboxA.order).isEqualTo(0)
        assertThat(mailboxB.order).isEqualTo(1)
    }

    @Test
    fun `should receive parent types before child types`() {
        // FYI -- it is important that mailboxes subscribe *not* in class
        // hierarchy order, so that the test can verify messages are received
        // in the correct order nonetheless
        val ordering = AtomicInteger()
        val rightMailbox =
            orderedMailbox<RightType>(ordering).subscribeTo(bus)
        val farRightMailbox =
            orderedMailbox<FarRightType>(ordering).subscribeTo(bus)
        val baseMailbox =
            orderedMailbox<BaseType>(ordering).subscribeTo(bus)
        val allMailbox =
            orderedMailbox<Any>(ordering).subscribeTo(bus)

        bus.post(FarRightType())

        assertThat(allMailbox.order).isEqualTo(0)
        assertThat(baseMailbox.order).isEqualTo(1)
        assertThat(rightMailbox.order).isEqualTo(2)
        assertThat(farRightMailbox.order).isEqualTo(3)
    }

    @Test
    fun `should unsubscribe exact type`() {
        val mailbox = discard<LeftType>().subscribeTo(bus)

        mailbox.unsubscribeFrom(bus)

        assertThat(bus.subscribers<LeftType>()).isEmpty()
    }

    @Test
    fun `should unsubscribe exact mailbox`() {
        val mailboxForDelivery = discard<RightType>().subscribeTo(bus)
        val mailboxForNoDelivery = discard<RightType>().subscribeTo(bus)

        bus -= mailboxForNoDelivery

        assertThat(bus.subscribers<RightType>())
            .containsOnly(mailboxForDelivery)

        println(bus.subscribers<RightType>())
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

        assertThat(bus.subscribers<RightType>())
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
        assertThat(bus.subscribers<RightType>()).isEqualTo(mailboxes)
    }

    @Test
    fun `should provide accurate details on dead letters`() {
        val message = RightType()

        bus.post(message)

        assertThat(returned).containsOnly(ReturnedMessage(bus, message))
    }

    @Test
    fun `should have default rejected letter box`() {
        val defaultMailboxCountForReturnedAndFailedMessages = 2

        assertThat(bus.subscribers<FailedMessage<Any>>())
            .hasSize(defaultMailboxCountForReturnedAndFailedMessages)
    }

    @Test
    fun `should deliver to first subscriber for returned messages before default`() {
        val subscribers = bus.subscribers<ReturnedMessage<Any>>()

        assertThat(subscribers.first().toString()).isEqualTo(
            "DEFAULT-DEAD-LETTERBOX"
        )
    }

    @Test
    fun `should deliver to first subscriber for failed messages before default`() {
        val subscribers = bus.subscribers<FailedMessage<Any>>()

        assertThat(subscribers.first().toString())
            .isEqualTo("DEFAULT-FAILED-LETTERBOX")
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

        assertThat(failed)
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
        AssertDelivery(delivered.flatMap { it.messages }, returned, failed)

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

    private fun allMailboxes() = listOf<TestMailbox<Any>>()
}

private data class OrderedMailbox<T>(
    private val masterOrder: AtomicInteger,
    private val messageType: Class<T>,
    private val myOrder: AtomicInteger = AtomicInteger(-1),
) : Mailbox<T> {
    val order: Int get() = myOrder.get()

    override fun invoke(message: T) =
        myOrder.set(masterOrder.getAndIncrement())

    override fun toString() =
        "TEST-ORDERED-MAILBOX<${messageType.simpleName}@$myOrder>"
}

private inline fun <reified T> orderedMailbox(order: AtomicInteger) =
    OrderedMailbox(order, T::class.java)

private fun <T : Any> failWith(failure: () -> Throwable): Mailbox<T> =
    { throw failure() }

private abstract class BaseType
private class LeftType : BaseType()
private open class RightType : BaseType()
private class FarRightType : RightType()

private class AssertDelivery<T>(
    private val delivered: List<T>,
    private val returned: List<ReturnedMessage<*>>,
    private val failed: List<FailedMessage<*>>,
) {
    fun noMessageDelivered() = apply {
        assertThat(delivered).isEmpty()
    }

    fun <U : T?> deliveredInOrder(vararg delivered: U) = apply {
        assertThat(this.delivered).isEqualTo(delivered.toList())
    }

    fun noMessageReturned() = apply {
        assertThat(returned).isEmpty()
    }

    fun returnedInOrder(vararg returned: ReturnedMessage<*>) = apply {
        assertThat(this.returned).isEqualTo(returned.toList())
    }

    fun noFailingMailbox() = apply {
        assertThat(failed).isEmpty()
    }

    fun failedInOrder(vararg failed: FailedMessage<*>) = apply {
        assertThat(this.failed).isEqualTo(failed.toList())
    }
}
