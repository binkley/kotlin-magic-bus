<a href="LICENSE.md">
<img src="https://unlicense.org/pd-icon.png" alt="Public Domain" align="right"/>
</a>

# Kotlin Magic Bus

[![build](https://github.com/binkley/kotlin-magic-bus/workflows/build/badge.svg)](https://github.com/binkley/kotlin-magic-bus/actions)
[![issues](https://img.shields.io/github/issues/binkley/kotlin-magic-bus.svg)](https://github.com/binkley/kotlin-magic-bus/issues/)
[![vulnerabilities](https://snyk.io/test/github/binkley/kotlin-magic-bus/badge.svg)](https://snyk.io/test/github/binkley/kotlin-magic-bus)
[![license](https://img.shields.io/badge/license-Public%20Domain-blue.svg)](http://unlicense.org/)

Kotlin version of self-messaging

(For an earlier source using Java as the source language, see
[the original Java version](https://github.com/binkley/magic-bus).)

## What is this?

_Magic Bus_ is an _internal_ message bus for JVM programs to talk with
themselves. It uses type-inheritance of messages, not string topics, for
subscribers indicate interest in posted messages. Methods are type-safe.

## Build

Use `./gradlew build` (Gradle) or `./batect build` (Batect) to build, run
tests.

## Examples

### Post a message

```kotlin
val bus: MagicBus // assigned elsewhere

bus.post(UUID.randomUUID()) // Only received by subscribers of `UUID` JVM type
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

### Process all messages, regardless of type or sender

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

### Process dead letters or failed posts

See [_Make me a bus_](#make-me-a-bus) (next section) for an example that 
saves dead letters or failed posts.

### Make me a bus

Use `hm.binkley.labs.MagicBusKt.DEFAULT_BUS` for a single-threaded bus that 
by default discards `ReturnedMessage` and `FailedMessage` posts.  
`SimpleMessageBusTest` uses a bus that tracks these posts.

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

### Introspection

You may examine mapping of message types to subscribers using
`MagicBus.subscribers`.  This may be useful in testing or debugging.  This 
mapping represents _internal state_ of your message bus, so be cautious in 
using this mapping.

## Implementation

* Pure JDK, no 3<sup>rd</sup>-party dependencies
* _Almost_ 100% test coverage
* Static code analysis is happy: [Detekt](https://detekt.github.io/detekt/)
  [Ktlint](https://ktlint.github.io/), 
  and [DependencyCheck](https://owasp.org/www-project-dependency-check/)
* Focus is on functions, not types, for subscriptions and bus behavior
* Deep recursion among mailboxen results in stack overflow; however, 
  triggering this takes a message post storm of typically 1000+, and this 
  should indicate the messaging patterns are very smelly

## TODO

* Should `DEFAULT_BUS` be a pre-defined global?
* Greater null-safety in declarations (`*` vs `T : Any`). See
  [_Difference between "*" and "Any" in Kotlin
  generics_](https://stackoverflow.com/a/40139892)
