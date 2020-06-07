# Magic Bus

A revivification of an old project

## What is this?

_Magic Bus_ is an an _internal_ message bus for JVM programs.  It uses
message type-inheritance, not string topics, for subscribers to receive
dispatched messages.  Methods are type-safe (as far as Java generics allows).

## Examples

### Post a message

```java
public final class SendOff {
    public SendOff(final MagicBus bus) {
        bus.post(UUID.randomUUID());
    }
}
```

### Receive messages

```java
public final class DiscussNumbersWithOneself {
    public DiscussNumbersWithOneself(final MagicBus bus) {
        bus.subscribe(Number.class,
                message -> System.out.println(String.format("%d", message)));
        bus.post(1);
        bus.post(3.14f);
        bus.post(new BigDecimal("1000000"));
    }
}
```

### See all messages, regardless of type or sender

```java
public final class Debugging {
    public Debugging(final MagicBus bus) {
        bus.subscribe(Object.class, this::recieve);
    }

    public void receive(final Object message) {
        System.out.println(message);
    }
}
```

### Stop receiving messages

```java
public final class QuitListening {
    private final MagicBus.Mailbox<Exception> receiver
            = e -> e.printStackTrace();

    public QuitListening(final MagicBus bus) {
        bus.subscribe(Exception.class, receiver);
    }

    public void silenceIsGolden() {
        bus.unsubscribe(Exception.class, receiver);
    }
}
```

### Make me a bus

```java
public final class VariationOnABus {
    public static void main(final String... args) {
        SimpleMagicBus.of(
                returned -> System.err.println(
                        "Bug: No receiver: " + returned),
                failed -> {
                    System.err.println(failed);
                    failed.failure.printStackTrace();
                },
                (mailbox, observed) ->
                        System.out.println(mailbox + " " + observed));
    }
}
```

```java
public final class QuieterVariationOnABus {
    public static void main(final String... args) {
        SimpleMagicBus.of(
                returned -> System.err.println(
                        "Bug: No receiver: " + returned),
                failed -> {
                    System.err.println(failed);
                    failed.failure.printStackTrace();
                },
                SimpleMagicBus.ignored());
    }
}
```

## Tech features

* Pure JDK, no 3<sup>rd</sup> parties
* _Almost_ 100% test coverage
* Spotbugs is happy
* Focus is on functions, not types, for subscriptions and bus behavior
