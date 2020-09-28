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

_Magic Bus_ is an _internal_ message bus for JVM programs to talk with
themselves. It uses type-inheritance of messages, not string topics, for
subscribers indicate interest in posted messages. Methods are type-safe.

## Build

Use `./gradlew build` (Gradle) or `./batect build` (Batect) to build, run
tests.

[Batect](https://batect.dev/) works "out of the box", however, an important
optimization is to avoid redownloading plugins and dependencies from within a
Docker container.

## Examples

### Post a message

```kotlin
val bus: MagicBus // assigned elsewhere

bus.post(UUID.randomUUID()) // Only received by subscribers of `UUID`
```

### Receive messages

```kotlin
val bus: MagicBus // assigned elsewhere

bus.subscribe<Number> { message ->
    println("$message")
}

bus.post(1) // An Int is a Number
bus.post(3.14f) // An Float is a Number
bus.post(BigDecimal("1000000")) // A BigDecimal is a Number
bus.post("Frodo lives!") // Nothing happens: not a Number
```

### See all messages, regardless of type or sender

```kotlin
val bus: MagicBus // assigned elsewhere

bus.subscribe<Any> { message ->
    println(message) // Messages are never null; any type of message
}
```

### Stop receiving messages

```kotlin
val bus: MagicBus // assigned elsewhere

val mailbox: Mailbox<SomeType> = { println(it) }
bus.subscribe(mailbox)

bus.post(SomeType()) // printed

bus.unsubscribe(mailbox) // Stop receiving messages

bus.post(SomeType()) // Not printed
```

### Make me a bus

```kotlin
// Track returned (no mailbox) messages, and failed (mailbox throws exception)
// messages, say for testing
val returned = mutableListOf<ReturnedMessage>()
val failed = mutableListOf<FailedMessage>()
val bus = SimpleMagicBus().apply {
    subscribe<ReturnedMessage> {
        returned += it
    }
    subscribe<FailedMessage> {
        failed += it
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

* Finding all subscribers (`Any`).  See disabled test in `SimpleMagicBusTest`
* Deep messaging paths (1K+): First try at `DeepRecursive` did not go well
* Greater null-safety in declarations (`*` vs `T : Any`).
  See https://stackoverflow.com/a/40139892
* Avoid use of JDK class reflection, ie, `SimpleMagicBus.subscribers`. This
  method needs cleanup in any case
