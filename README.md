<a href="LICENSE.md">
<img src="https://unlicense.org/pd-icon.png" alt="Public Domain" align="right"/>
</a>

# Kotlin Magic Bus

[![build](https://github.com/binkley/kotlin-magic-bus/workflows/build/badge.svg)](https://github.com/binkley/kotlin-magic-bus/actions)
[![issues](https://img.shields.io/github/issues/binkley/kotlin-magic-bus.svg)](https://github.com/binkley/kotlin-magic-bus/issues/)
[![vulnerabilities](https://snyk.io/test/github/binkley/kotlin-magic-bus/badge.svg)](https://snyk.io/test/github/binkley/kotlin-magic-bus)
[![license](https://img.shields.io/badge/license-Public%20Domain-blue.svg)](http://unlicense.org/)

**Kotlin version of self-messaging**

(For an earlier version using Java as the source language, see
[the original Java version](https://github.com/binkley/magic-bus).)

## What is this?

_Magic Bus_ is an _internal_ message bus for JVM programs to talk with
themselves. The bus uses type-inheritance of messages, not string topics, and
subscribers indicate interest in posted messages by type. Methods are
type-safe.

The goal is to support
[messaging patterns](https://www.enterpriseintegrationpatterns.com/patterns/messaging/)
within a single JVM process (program).

## Build

Use `./gradlew build` (Gradle) or `./batect build` (Batect) to build, run
tests.  CI for the repo uses Batect to validate pushes.

## Terminology

- _Letter_ (noun) &mdash; synonym for "message": this is a JVM object of some
  instance type to be processed by other objects or functions. The nouns,
  "letter" and "message" may be used interchangeably depending on context 
  or on common industry usage.  Typically, this is a business or logical 
  type to process by your system
- _Mailbox_ (noun) \[pl: mailboxen] &mdash; a function or method that 
  receives letters, and processes them in some fashion (possibly 
  discarding them).  Typically these manifest your business or processing 
  logic
- _Post_ (verb) &mdash; to send a letter. The poster does not know which 
  mailboxen may process the letter.  Typically, your domain boundaries post 
  letters for other internal domains to process

And:

- _Failed_ (adj) &mdash; a mailbox raises an exception while processing a
  letter. This always indicates an error in design or mailbox logic.  
  **Note**: For JVM, distinguish between `Exception` (a program concern)
  and `Error` (a JVM concern). `SimpleMagicBus` posts mailbox `Exception`s as
  `FailedMessage`s, and `Error`s bubble out
- _Returned_ (adj) &mdash; a posted letter with no subscribed mailbox to
  receive. Typically, this indicates a potential error in program design or
  logic. `SimpleMagicBus` by default discards returned messages

## Examples

### Post messages

Any JVM type can be published as a message. A receiver (a
receiver/subscriber is termed a `Mailbox` in code) receives new posts 
based on typing.

```kotlin
val bus: MagicBus // assigned elsewhere

bus.post(UUID.randomUUID()) // Only received by subscribers of `UUID` JDK type
```

### Receive messages

By choice, this library does not use annotations to note subscribers or 
publishers.

```kotlin
val bus: MagicBus // assigned elsewhere

// A mailbox (subscriber) is just a function; could be a class or Kotlin 
// object which implements the function type (shape).  This example just 
// prints the message to STDOUT
bus.subscribe<Number> { message ->
    println("$message")
}

bus.post(1) // An Int is a Number -- PRINTED
bus.post(3.14f) // An Float is a Number -- PRINTED
bus.post(BigDecimal("1000000")) // A BigDecimal is a Number -- PRINTED
bus.post("Frodo lives!") // Nothing happens: not a Number -- NOT PRINTED
```

Similar example using Kotlin objects (could be a class instance):
```kotlin
val bus: MagicBus // assigned elsewhere

bus.subscribe(object : Mailbox<Number> {
    // `invoke` is Kotlin-specific; the technique varies by source language
    override fun invoke(message: Number) {
        println("I AM A NUMBER! HEAR MY VALUE: $message")
    }
})
```

### Process all messages, regardless of type or sender

```kotlin
val bus: MagicBus // assigned elsewhere

// Example for debugging
bus.subscribe<Any> { message ->
    myDebugLog.log(message) // Messages are never null 
}
```

### Stop receiving messages

```kotlin
val bus: MagicBus // assigned elsewhere

val mailbox: Mailbox<SomeType> = { println(it) }
bus.subscribe(mailbox)

bus.post(SomeType()) // PRINTED

bus.unsubscribe(mailbox) // Stop receiving messages

bus.post(SomeType()) // NOT PRINTED
```

### Process dead letters or failed posts

See [_Make me a bus_](#make-me-a-bus) (next section) for an example that 
saves dead letters or failed posts.

### Make me a bus

Use `MagicBusKt.DEFAULT_BUS` for a global single-threaded bus that by default
discards `ReturnedMessage` and `FailedMessage` posts.  
`TestMagicBus` in `SimpleMagicBusTest` extends to track all posts for testing.

**Typical programs would add handling for `ReturnedMessage` (no subscriber)
and `FailedMessage` (a subscriber "blew up").**  This library does not include
helpful default handlers for these cases: typical strategies include logging
or business logic, both which are beyond scope of this library.

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
An alternative using class extension:
```kotlin
// Track returned (no mailbox) messages, and failed (mailbox throws exception)
// messages, say for testing
class ExampleRecordingMagicBus: SimpleMagicBus() {
    private val _returned = mutableListOf<ReturnedMessage>()
    private val _failed = mutableListOf<FailedMessage>()

    // Typical pattern for visible but not modifiable  
    val returned: List<ReturnedMessage> get() = _returned
    val failed: List<ReturnedMessage> get() = _failed

    init {
        subscribe<ReturnedMessage> {
            _returned += it
        }
        subscribe<FailedMessage> {
            _failed += it
        }
    }
}
```

Or, if you're _lazy_ like me (pun intended; see
[implementation](src/main/kotlin/hm/binkley/labs/MagicBus.kt)).  
`DEFAULT_BUS` discards `ReturnedMessage` and `FailedMessage` posts by default.
Add mailboxen to act on these posted message types.

```kotlin
fun main() {
    val globalBus = DEFAULT_GLOBAL_BUS
    // Provide `globalBus`, for example, to Spring Framework for injection
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
* No attempt is made to detect unbounded loops and cycles.  So if you 
  repost from within a mailbox, take care that the mailbox itself does not 
  receive the reposting, or include properties in the messages to end the 
  cycle, or otherwise you will get a `StackOverflowError` (detecting when 
  reposting might terminate is an NP-hard problem akin to
  [the halting problem](https://en.wikipedia.org/wiki/Halting_problem))

## TODO

* Should `DEFAULT_BUS` be a pre-defined global?
* Greater null-safety in declarations (`*` vs `T : Any`). See
  [_Difference between "*" and "Any" in Kotlin
  generics_](https://stackoverflow.com/a/40139892)
* Consider breaking `MagicBus` into more fine-grained interfaces (_eg_, 
  posting separate from subscribing separate from inspecting).  See 
  [_Namespacing in Kotlin_](https://arturdryomov.dev/posts/namespacing-in-kotlin/)
  for a discussion of implementation
