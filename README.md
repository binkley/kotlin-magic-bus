<a href="LICENSE.md">
<img src="https://unlicense.org/pd-icon.png" alt="Public Domain" align="right"/>
</a>

# Kotlin Magic Bus

[![build](https://github.com/binkley/kotlin-magic-bus/workflows/build/badge.svg)](https://github.com/binkley/kotlin-magic-bus/actions)
[![issues](https://img.shields.io/github/issues/binkley/kotlin-magic-bus.svg)](https://github.com/binkley/kotlin-magic-bus/issues/)
[![Public Domain](https://img.shields.io/badge/license-Public%20Domain-blue.svg)](http://unlicense.org/)
[![made with kotlin](https://img.shields.io/badge/made%20with-Kotlin-1f425f.svg)](https://kotlinlang.org/)

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
        bus.subscribe<Number> { message ->
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
        bus.subscribe<Any> { message ->
            println(message)
        }
    }
}
```

### Stop receiving messages

```kotlin
class QuitListening(private val bus: MagicBus) {
    private val mailbox: Mailbox<Exception> = { e -> e.printStackTrace() }

    init {
        bus.subscribe(mailbox)
    }

    fun silenceIsGolden() {
        bus.unsubscribe(mailbox)
    }
}
```

### Make me a bus

```kotlin
class VariationOnABus {
    fun main() {
        val returned = mutableListOf<ReturnedMessage>()
        val failed = mutableListOf<FailedMessage>()

        SimpleMagicBus().apply {
            subscribe<ReturnedMessage> {
                returned += it
            }
            subscribe<FailedMessage> {
                failed += it
            }
        }
    }
}
```

Or, if you're _lazy_ like me (pun intended; see
[implementation](src/main/kotlin/hm/binkley/labs/MagicBus.kt)):
```kotlin
class BeGlobal {
    fun main() {
        val bus = DEFAULT_GLOBAL_BUS
        // Do bus stuff
    }
}
```

## Tech features

* Pure JDK, no 3<sup>rd</sup> parties
* _Almost_ 100% test coverage
* [Detekt](https://detekt.github.io/detekt/) and
  [Ktlint](https://ktlint.github.io/) are happy
* Focus is on functions, not types, for subscriptions and bus behavior

## TODO

* JaCoCo does not recognize branch coverage for "impossible" branches within
  the Kotlin stdlib (`MutableCollection.remove`).
* Pick one: Detekt or Ktlint.
