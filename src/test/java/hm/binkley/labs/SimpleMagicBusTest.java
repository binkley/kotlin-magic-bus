package hm.binkley.labs;

import hm.binkley.labs.MagicBus.FailedMessage;
import hm.binkley.labs.MagicBus.Mailbox;
import hm.binkley.labs.MagicBus.ReturnedMessage;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static hm.binkley.labs.SimpleMagicBus.ignored;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.IntStream.range;
import static lombok.AccessLevel.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("rawtypes")
        // TODO: Clean up javac warns
class SimpleMagicBusTest {
    private final List<ReturnedMessage> returned
            = new CopyOnWriteArrayList<>();
    private final List<FailedMessage> failed = new CopyOnWriteArrayList<>();
    private final Map<Mailbox, List<Object>> observed = new LinkedHashMap<>();

    private MagicBus bus;

    private static <T> List<T> noMailbox() {
        return emptyList();
    }

    private static <T> Mailbox<T> record(final AtomicInteger order,
            final AtomicInteger record) {
        return __ -> record.set(order.getAndIncrement());
    }

    private static <T, E extends Exception> Mailbox<T> failWith(
            final Supplier<E> ctor) {
        return __ -> {
            throw ctor.get();
        };
    }

    @BeforeEach
    void setUp() {
        bus = SimpleMagicBus.of(returned::add, failed::add,
                (mailbox, message) -> observed
                        .computeIfAbsent(mailbox, __ -> new ArrayList<>())
                        .add(message));
    }

    @Test
    void shouldRejectMissingReturnedHandlerInConstructor() {
        assertThatThrownBy(
                () -> SimpleMagicBus.of(null, failed::add, ignored()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingFailedHandlerInConstructor() {
        assertThatThrownBy(
                () -> SimpleMagicBus.of(returned::add, null, ignored()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingObservedHandlerInConstructor() {
        assertThatThrownBy(
                () -> SimpleMagicBus.of(returned::add, failed::add, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMessageTypeInSubscribe() {
        assertThatThrownBy(() -> bus.subscribe(null, __ -> {
        })).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMailboxInSubscribe() {
        assertThatThrownBy(() -> bus.subscribe(RightType.class, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMessageTypeInUnsubscribe() {
        assertThatThrownBy(() -> bus.unsubscribe(null, __ -> {
        })).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMailboxInUnsubscribe() {
        assertThatThrownBy(() -> bus.unsubscribe(RightType.class, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMessageInPublish() {
        assertThatThrownBy(() -> bus.post(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReceiveCorrectType() {
        final var messages = new ArrayList<RightType>(1);
        bus.subscribe(RightType.class, messages::add);
        final var message = new RightType();
        bus.post(message);
        assertOn(messages).delivered(message).noneReturned().noneFailed();
    }

    @Test
    void shouldNotReceiveWrongType() {
        final var messages = new ArrayList<LeftType>(1);
        bus.subscribe(LeftType.class, messages::add);
        final var message = new RightType();
        bus.post(message);
        assertOn(messages).noneDelivered().returned(this.with(message))
                .noneFailed();
    }

    @Test
    void shouldReceiveSubtypes() {
        final var messages = new ArrayList<BaseType>(1);
        bus.subscribe(BaseType.class, messages::add);
        final var message = new RightType();
        bus.post(message);
        assertOn(messages).delivered(message).noneReturned().noneFailed();
    }

    @Test
    void shouldSaveDeadLetters() {
        final var message = new LeftType();
        bus.post(message);
        assertOn(noMailbox()).noneDelivered().returned(this.with(message))
                .noneFailed();
    }

    @Test
    void shouldSaveFailedPosts() {
        final var failure = new Exception();
        final Mailbox<LeftType> mailbox = failWith(() -> failure);
        bus.subscribe(LeftType.class, mailbox);
        final var message = new LeftType();
        bus.post(message);
        assertOn(noMailbox()).noneDelivered().noneReturned()
                .failed(this.with(mailbox, message, failure));
    }

    @Test
    void shouldThrowFailedPostsForUnchecked() {
        assertThatThrownBy(() -> {
            bus.subscribe(LeftType.class, failWith(RuntimeException::new));
            bus.post(new LeftType());
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReceiveEarlierSubscribersFirst() {
        final var delivery = new AtomicInteger();
        final var second = new AtomicInteger();
        final var first = new AtomicInteger();
        final var fourth = new AtomicInteger();
        final var third = new AtomicInteger();
        bus.subscribe(RightType.class, record(delivery, first));
        bus.subscribe(RightType.class, record(delivery, second));
        bus.subscribe(RightType.class, record(delivery, third));
        bus.subscribe(RightType.class, record(delivery, fourth));
        bus.post(new RightType());
        assertThat(first.get()).isEqualTo(0);
        assertThat(second.get()).isEqualTo(1);
        assertThat(third.get()).isEqualTo(2);
        assertThat(fourth.get()).isEqualTo(3);
        assertOn(noMailbox()).noneReturned().noneFailed();
    }

    @Test
    void shouldReceiveOnParentTypeFirstInParentFirstOrder() {
        final var delivery = new AtomicInteger();
        final var farRight = new AtomicInteger();
        final var right = new AtomicInteger();
        final var base = new AtomicInteger();
        final var object = new AtomicInteger();
        bus.subscribe(RightType.class, record(delivery, right));
        bus.subscribe(FarRightType.class, record(delivery, farRight));
        bus.subscribe(Object.class, record(delivery, object));
        bus.subscribe(BaseType.class, record(delivery, base));
        bus.post(new FarRightType());
        assertThat(object.get()).isEqualTo(0);
        assertThat(base.get()).isEqualTo(1);
        assertThat(right.get()).isEqualTo(2);
        assertThat(farRight.get()).isEqualTo(3);
        assertOn(noMailbox()).noneReturned().noneFailed();
    }

    @Test
    void shouldUnsubscribeOnlyMailbox() {
        final var messages = new ArrayList<LeftType>(0);
        final Mailbox<LeftType> mailbox = messages::add;
        bus.subscribe(LeftType.class, mailbox);
        bus.unsubscribe(LeftType.class, mailbox);
        final var message = new LeftType();
        bus.post(message);
        assertOn(messages).noneDelivered().returned(this.with(message))
                .noneFailed();
    }

    @Test
    void shouldUnsubscribeRightMailbox() {
        final var messagesA = new ArrayList<RightType>(1);
        final Mailbox<RightType> mailboxB = failWith(Exception::new);
        bus.subscribe(RightType.class, messagesA::add);
        bus.subscribe(RightType.class, mailboxB);
        bus.unsubscribe(RightType.class, mailboxB);
        final var message = new RightType();
        bus.post(message);
        assertOn(messagesA).delivered(message).noneReturned().noneFailed();
    }

    @Test
    void shouldUnsubscribeThreadSafely() {
        await().atMost(2000L, MILLISECONDS).until(() -> {
            final var latch = new CountDownLatch(100);
            range(0, 100).parallel().forEach((actor) -> {
                final Mailbox<RightType> mailbox = new Discard();
                bus.subscribe(RightType.class, mailbox);
                bus.unsubscribe(RightType.class, mailbox);
                latch.countDown();
            });
            assertThat(latch.await(1000L, MILLISECONDS)).isNotEqualTo(0);
            final var message = new RightType();
            bus.post(message);
            assertOn(noMailbox()).returned(this.with(message)).noneFailed();
            return true;
        });
    }

    @Test
    void shouldComplainWhenUnsubscribingBadMailbox() {
        assertThatThrownBy(() -> {
            bus.subscribe(RightType.class, __ -> {
            });
            final Mailbox<RightType> mailbox = __ -> {
            };
            bus.unsubscribe(RightType.class, mailbox);
        }).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldComplainWhenUnsubscribingBadMessageType() {
        assertThatThrownBy(() -> {
            final Mailbox<RightType> mailbox = __ -> {
            };
            bus.unsubscribe(RightType.class, mailbox);
        }).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldProvideSubscriberForMessageType() {
        final Mailbox<RightType> a = new Mailbox<>() {
            @Override
            public void receive(final RightType message) {
            }

            @Override
            public String toString() {
                return "b";
            }
        };
        final Mailbox<BaseType> b = new Mailbox<>() {
            @Override
            public void receive(final BaseType message) {
            }

            @Override
            public String toString() {
                return "a";
            }
        };
        bus.subscribe(BaseType.class, b);
        bus.subscribe(RightType.class, a);

        assertThat(((SimpleMagicBus) bus).subscribers(RightType.class))
                .isEqualTo(List.of(b, a));
    }

    private <T> AssertDelivery<T> assertOn(final List<T> delivered) {
        return new AssertDelivery<>(delivered);
    }

    private ReturnedMessage with(final Object message) {
        return new ReturnedMessage(bus, message);
    }

    private FailedMessage with(final Mailbox<?> mailbox, final Object message,
            final Exception failure) {
        return new FailedMessage(bus, mailbox, message, failure);
    }

    private static final class FarRightType
            extends RightType {
    }

    private static class RightType
            extends BaseType {
    }

    private static final class LeftType
            extends BaseType {
    }

    private abstract static class BaseType {
        BaseType() {
        }
    }

    @NoArgsConstructor(access = PRIVATE)
    private static class Discard
            implements Mailbox<RightType> {
        public void receive(final RightType message) {
        }
    }

    private final class AssertDelivery<T> {
        private final List<T> delivered;

        // TODO: Javac is choking when using @RequiredArgsCtor
        @java.beans.ConstructorProperties({"delivered"})
        private AssertDelivery(final List<T> delivered) {
            this.delivered = delivered;
        }

        private AssertDelivery<T> noneDelivered() {
            assertThat(this.delivered).isEmpty();
            return this;
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        private <U extends T> AssertDelivery<T> delivered(
                final U... delivered) {
            assertThat(this.delivered).containsExactly(delivered);
            return this;
        }

        private AssertDelivery<T> noneReturned() {
            assertThat(SimpleMagicBusTest.this.returned).isEmpty();
            return this;
        }

        private AssertDelivery<T> returned(
                final ReturnedMessage... returned) {
            assertThat(SimpleMagicBusTest.this.returned)
                    .containsExactly(returned);
            return this;
        }

        private AssertDelivery<T> noneFailed() {
            assertThat(SimpleMagicBusTest.this.failed).isEmpty();
            return this;
        }

        private AssertDelivery<T> failed(final FailedMessage... failed) {
            assertThat(SimpleMagicBusTest.this.failed)
                    .containsExactly(failed);
            return this;
        }
    }
}
