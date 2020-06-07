package hm.binkley.labs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor(staticName = "of")
@SuppressWarnings({"rawtypes", "unchecked"}) // TODO: Clean up javac warns
public final class SimpleMagicBus
        implements MagicBus {
    private final Subscribers subscribers = new Subscribers();
    @NonNull
    private final Consumer<? super ReturnedMessage> returned;
    @NonNull
    private final Consumer<? super FailedMessage> failed;
    @NonNull
    private final BiConsumer<Mailbox, Object> observed;

    public static BiConsumer<Mailbox, Object> ignored() {
        return (mailbox, observed) -> {
        };
    }

    private static Consumer<Mailbox> record(final AtomicInteger deliveries) {
        return subscriber -> deliveries.incrementAndGet();
    }

    public <T> void subscribe(final Class<T> messageType,
            final Mailbox<? super T> mailbox) {
        subscribers.subscribe(requireNonNull(messageType, "messageType"),
                requireNonNull(mailbox, "mailbox"));
    }

    public <T> void unsubscribe(final Class<T> messageType,
            final Mailbox<? super T> mailbox) {
        subscribers.unsubscribe(requireNonNull(messageType, "messageType"),
                requireNonNull(mailbox, "mailbox"));
    }

    @SuppressFBWarnings("RCN") // TODO: Spotbugs know about onClose?
    public void post(final Object message) {
        try (final Stream<Mailbox> mailboxes = subscribers
                .of(requireNonNull(message, "message").getClass())) {
            final AtomicInteger deliveries = new AtomicInteger();
            mailboxes.onClose(() -> returnIfDead(deliveries, message))
                    .peek(record(deliveries)).forEach(receive(message));
        }
    }

    public <T> List<Mailbox<? super T>> subscribers(
            final Class<T> messageType) {
        return subscribers.of(messageType)
                .map(mailbox -> (Mailbox<T>) mailbox).collect(toList());
    }

    private Consumer<Mailbox> receive(final Object message) {
        return (mailbox) -> {
            try {
                observed.accept(mailbox, message);
                mailbox.receive(message);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                failed.accept(new FailedMessage(this, mailbox, message, e));
            }
        };
    }

    private void returnIfDead(final AtomicInteger deliveries,
            final Object message) {
        if (0 == deliveries.get()) {
            returned.accept(new ReturnedMessage(this, message));
        }
    }

    private static final class Subscribers {
        private final Map<Class, Set<Mailbox>> subscribers
                = new ConcurrentSkipListMap<>(Subscribers::classOrder);

        private static int classOrder(final Class<?> a, final Class<?> b) {
            final boolean aFirst = b.isAssignableFrom(a);
            final boolean bFirst = a.isAssignableFrom(b);

            if (aFirst && !bFirst) return 1;
            else if (bFirst && !aFirst) return -1;
            else return 0;
        }

        private static <T> Set<Mailbox<T>> mailboxes(
                final Class<T> messageType) {
            return new CopyOnWriteArraySet<>();
        }

        private static Predicate<Entry<Class, Set<Mailbox>>> subscribedTo(
                final Class messageType) {
            return e -> e.getKey().isAssignableFrom(messageType);
        }

        private static Function<Entry<Class, Set<Mailbox>>, Stream<Mailbox>> toMailboxes() {
            return e -> e.getValue().stream();
        }

        private synchronized <T> void subscribe(final Class<T> messageType,
                final Mailbox<? super T> mailbox) {
            subscribers.computeIfAbsent(messageType, type -> mailboxes(type))
                    .add(mailbox);
        }

        private synchronized void unsubscribe(final Class messageType,
                final Mailbox mailbox) {
            final Set<Mailbox> mailboxes = subscribers.get(messageType);
            if (null == mailboxes) throw new NoSuchElementException();
            if (!mailboxes.remove(mailbox))
                throw new NoSuchElementException();
        }

        private synchronized Stream<Mailbox> of(final Class messageType) {
            return subscribers.entrySet().stream()
                    .filter(subscribedTo(messageType)).flatMap(toMailboxes());
        }
    }
}
