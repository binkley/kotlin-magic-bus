<a href="LICENSE.md">
<img src="https://unlicense.org/pd-icon.png" alt="Public Domain" align="right"/>
</a>

# Kotlin Magic Bus

Kotlin version of self-messaging

(See [the original Java version](https://github.com/binkley/magic-bus).)

## What is this?

_Magic Bus_ is an an _internal_ message bus for JVM programs to talk with
themselves.  It uses message type-inheritance, not string topics, for
subscribers to receive dispatched messages.  Methods are type-safe.

## Examples

### Post a message

```kotlin
class SendOff(bus: MagicBus) {
    init {
        bus.post(UUID.randomUUID())
    }
}
```

### Receive messages

```kotlin
class DiscussNumbersWithOneself(bus: MagicBus) {
    init {
        bus.subscribe(Number::class.java) { message ->
            println("$message")
        }
        bus.post(1)
        bus.post(3.14f)
        bus.post(BigDecimal("1000000"))
    }
}
```

### See all messages, regardless of type or sender

```kotlin
class Debugging(bus: MagicBus) {
    init {
        bus.subscribe(Any::class.java) { message ->
            println(message)
        }
    }
}
```

### Stop receiving messages

```kotlin
class QuitListening(private val bus: MagicBus) {
    private val receiver: Mailbox<Exception> = { e -> e.printStackTrace() }

    init {
        bus.subscribe(Exception::class.java, receiver)
        // Or: receiver.deliverFrom(bus)
    }

    fun silenceIsGolden() {
        bus.unsubscribe(Exception::class.java, receiver)
        // Or: receiver.noDeliveryFrom(bus)
    }
}
```

### Make me a bus

```kotlin
class VariationOnABus {
    fun main() {
        SimpleMagicBus.of(
                { returned -> println("BUG: No receiver: $returned") },
                { failed ->
                    System.err.println(failed);
                    failed.failure.printStackTrace();
                })
                { mailbox, observed -> println(mailbox + " " + observed) }
    }
}
```

## Tech features

* Pure JDK, no 3<sup>rd</sup> parties
* _Almost_ 100% test coverage
* Spotbugs is happy
* Focus is on functions, not types, for subscriptions and bus behavior

## TODO

* JaCoCo shows 100% instruction coverage, but says there are missed branches
* Replace use of `Stream` with `Sequence`
