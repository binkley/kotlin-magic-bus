package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.CoderMalfunctionError
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleMagicBusTest {
    private val returned = mutableListOf<ReturnedMessage<*>>()
    private val failed = mutableListOf<FailedMessage<*>>()
    private val delivered = mutableMapOf<Mailbox<*>, MutableList<Any>>()

    // Do *not* use `DEFAULT_BUS`.  The test needs a new instance each run.
    private val bus = SimpleMagicBus().also { bus ->
        namedMailbox<ReturnedMessage<Any>>("TEST-DEAD-LETTERBOX") {
            returned += it
        }.subscribeTo(bus)
        namedMailbox<FailedMessage<Any>>("TEST-FAILED-LETTERBOX") {
            failed += it
        }.subscribeTo(bus)
    }

    @Test
    fun `should receive correct type`() {
        val mailbox = testMailbox<RightType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noneReturnedToTestClass()
            .noneFailedForTestClass()
    }

    @Test
    fun `should deliver correctly to disparate subscribers`() {
        val mailboxForDelivery = testMailbox<RightType>().subscribeTo(bus)
        val mailboxForNoDelivery = testMailbox<LeftType>().subscribeTo(bus)

        assertThat(bus.subscribers<RightType>().toList())
            .isEqualTo(listOf(mailboxForDelivery))

        val message = RightType()

        bus.post(message)

        assertOn(mailboxForDelivery)
            .deliveredInOrder(message)
            .noneReturnedToTestClass()
            .noneFailedForTestClass()
        assertOn(mailboxForNoDelivery)
            .noneDeliveredToTestClass()
            .noneReturnedToTestClass()
            .noneFailedForTestClass()
    }

    @Test
    fun `should not receive wrong type`() {
        val mailbox = testMailbox<LeftType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .noneDeliveredToTestClass()
            .returnedInOrder(with(message))
            .noneFailedForTestClass()
    }

    @Test
    fun `should receive subtypes`() {
        val mailbox = testMailbox<BaseType>().subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertOn(mailbox)
            .deliveredInOrder(message)
            .noneReturnedToTestClass()
            .noneFailedForTestClass()
    }

    @Test
    fun `should save dead letters`() {
        val message = LeftType()

        bus.post(message)

        assertOn(allMailboxes())
            .noneDeliveredToTestClass()
            .returnedInOrder(with(message))
            .noneFailedForTestClass()
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
        val failureA = Exception()
        val brokenMailboxA = failWith<LeftType> { failureA }.subscribeTo(bus)
        val failureB = Exception()
        val brokenMailboxB = failWith<LeftType> { failureB }.subscribeTo(bus)
        val message = LeftType()

        bus.post(message)

        assertOn(allMailboxes())
            .noneDeliveredToTestClass()
            .noneReturnedToTestClass()
            .failedInOrder(
                with(brokenMailboxA, message, failureA),
                with(brokenMailboxB, message, failureB)
            )
    }

    @Test
    fun `should bubble out runtime exceptions`() {
        val failure = RuntimeException()
        assertThatThrownBy {
            failWith<LeftType> { failure }.subscribeTo(bus)

            bus.post(LeftType())
        }.isSameAs(failure)
    }

    @Test
    fun `should bubble out JVM errors (Error vs Exception)`() {
        val failure = CoderMalfunctionError(Exception())
        assertThatThrownBy {
            failWith<LeftType> { failure }.subscribeTo(bus)

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
            .noneReturnedToTestClass()
            .failedInOrder(failedMessage)
            .deliveredInOrder(message, failedMessage)
    }

    @Test
    fun `should be fatal for a failure mailbox to itself fail`() {
        failWith<RightType> { Exception() }.subscribeTo(bus)
        failWith<FailedMessage<Any>> { Exception() }.subscribeTo(bus)

        assertThrows<StackOverflowError> {
            bus.post(RightType())
        }
    }

    @Test
    fun `should receive mailboxes for same type in subscription order`() {
        val ordering = AtomicInteger()
        val mailbox1 = orderedMailbox<RightType>(ordering).subscribeTo(bus)
        val mailbox2 = orderedMailbox<RightType>(ordering).subscribeTo(bus)

        bus.post(RightType())

        assertThat(mailbox1.order).isEqualTo(0)
        assertThat(mailbox2.order).isEqualTo(1)
    }

    @Test
    fun `should receive parent types before child types`() {
        val ordering = AtomicInteger()
        val rightMailbox =
            orderedMailbox<RightType>(ordering).subscribeTo(bus)
        val farRightMailbox =
            orderedMailbox<FarRightType>(ordering).subscribeTo(bus)
        val allMailbox =
            orderedMailbox<Any>(ordering).subscribeTo(bus)
        val baseMailbox =
            orderedMailbox<BaseType>(ordering).subscribeTo(bus)

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

        mailboxForNoDelivery.unsubscribeFrom(bus)

        assertThat(bus.subscribers<RightType>())
            .isEqualTo(listOf(mailboxForDelivery))

        println(bus.subscribers<RightType>())
    }

    @Test
    fun `should fail to unsubscribe for message type`() {
        val mailbox = discard<RightType>()

        assertThrows<NoSuchElementException> {
            mailbox.unsubscribeFrom(bus)
        }
    }

    @Test
    fun `should fail to unsubscribe exact mailbox regardless of other mailboxes`() {
        discard<RightType>().subscribeTo(bus)
        val mailboxNotSubscribed = discard<RightType>()

        assertThrows<NoSuchElementException> {
            mailboxNotSubscribed.unsubscribeFrom(bus)
        }
    }

    @Test
    fun `should provide subscribers for message type in base-up order`() {
        // Subscription in derived-base order to show that the subscriber
        // list is in base-derived order
        val mailboxRight = testMailbox<RightType>().subscribeTo(bus)
        val mailboxBase = testMailbox<BaseType>().subscribeTo(bus)

        assertThat(bus.subscribers<RightType>().toList())
            .isEqualTo(listOf(mailboxBase, mailboxRight))
    }

    @Test
    fun `should provide subscribers for message type in subscribed-order`() {
        val mailboxes = (1..10).map {
            testMailbox<RightType>().subscribeTo(bus)
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

        assertThat(returned).isEqualTo(listOf(ReturnedMessage(bus, message)))
    }

    @Test
    fun `should have default rejected letter box`() {
        val defaultMailboxCountForReturnedAndFailedMessages = 2

        assertThat(bus.subscribers<FailedMessage<Any>>().toList())
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

        assertThat(subscribers.first().toString()).isEqualTo(
            "DEFAULT-REJECTED-LETTERBOX"
        )
    }

    @Test
    fun `should discard in the discard letter box`() {
        discard<RightType>().subscribeTo(bus)

        bus.post(RightType())

        assertOn(allMailboxes())
            .noneDeliveredToTestClass()
            .noneReturnedToTestClass()
            .noneFailedForTestClass()
    }

    @Test
    fun `should provide accurate details on failed posts`() {
        val failure = Exception()
        val mailbox = failWith<RightType> { failure }.subscribeTo(bus)
        val message = RightType()

        bus.post(message)

        assertThat(failed)
            .isEqualTo(listOf(FailedMessage(bus, mailbox, message, failure)))
    }

    @Test
    fun `should have named mailboxes`() {
        val name = "BOB'S YER UNKEL"
        val mailbox = namedMailbox<RightType>(name) {}

        assertThat(mailbox.toString()).isEqualTo(name)
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

private fun allMailboxes() = emptyList<Mailbox<Any>>()

private data class OrderedMailbox<T>(
    private val masterOrder: AtomicInteger,
    private val myOrder: AtomicInteger = AtomicInteger(),
) : Mailbox<T> {
    val order: Int get() = myOrder.get()

    override fun invoke(message: T) =
        myOrder.set(masterOrder.getAndIncrement())
}

private fun <T> orderedMailbox(order: AtomicInteger) =
    OrderedMailbox<T>(order)

private fun <T : Any> failWith(failure: () -> Throwable): Mailbox<T> =
    { throw failure() }

private abstract class BaseType
private class LeftType : BaseType()
private open class RightType : BaseType()
private class FarRightType : RightType()

private class AssertDelivery<T>(
    private val returned: List<ReturnedMessage<*>>,
    private val failed: List<FailedMessage<*>>,
    private val delivered: List<T>,
) {
    fun noneDeliveredToTestClass() = apply {
        assertThat(delivered).isEmpty()
    }

    fun <U : T?> deliveredInOrder(vararg delivered: U) = apply {
        assertThat(this.delivered).isEqualTo(delivered.toList())
    }

    fun noneReturnedToTestClass() = apply {
        assertThat(returned).isEmpty()
    }

    fun returnedInOrder(vararg returned: ReturnedMessage<*>) = apply {
        assertThat(this.returned).isEqualTo(returned.toList())
    }

    fun noneFailedForTestClass() = apply {
        assertThat(failed).isEmpty()
    }

    fun failedInOrder(vararg failed: FailedMessage<*>) = apply {
        assertThat(this.failed).isEqualTo(failed.toList())
    }
}
