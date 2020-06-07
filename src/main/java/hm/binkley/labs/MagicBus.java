package hm.binkley.labs;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

public interface MagicBus {
    <T> void subscribe(Class<T> messageType, Mailbox<? super T> mailbox);

    <T> void unsubscribe(Class<T> messageType, Mailbox<? super T> mailbox);

    void post(Object message);

    @FunctionalInterface
    interface Mailbox<T> {
        void receive(final T message)
                throws Exception;
    }

    @EqualsAndHashCode
    @RequiredArgsConstructor
    @ToString
    final class FailedMessage {
        public final MagicBus bus;
        public final Mailbox<?> mailbox;
        public final Object message;
        public final Exception failure;
    }

    @EqualsAndHashCode
    @RequiredArgsConstructor
    @ToString
    final class ReturnedMessage {
        public final MagicBus bus;
        public final Object message;
    }
}
