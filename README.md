<a href="./LICENSE.md">
<img src="./images/public-domain.svg" alt="Public Domain"
align="right" width="20%" height="auto"/>
</a>

# Kotlin Magic Bus

[![build](https://github.com/binkley/kotlin-magic-bus/workflows/build/badge.svg)](https://github.com/binkley/kotlin-magic-bus/actions)
[![issues](https://img.shields.io/github/issues/binkley/kotlin-magic-bus.svg)](https://github.com/binkley/kotlin-magic-bus/issues/)
[![pull requests](https://img.shields.io/github/issues-pr/binkley/kotlin-magic-bus.svg)](https://github.com/binkley/kotlin-magic-bus/pulls)
[![vulnerabilities](https://snyk.io/test/github/binkley/kotlin-magic-bus/badge.svg)](https://snyk.io/test/github/binkley/kotlin-magic-bus)
[![license](https://img.shields.io/badge/license-Public%20Domain-blue.svg)](http://unlicense.org/)

**Kotlin version of self-messaging**

(For an earlier version using Java as the source language, see
[the original Java version](https://github.com/binkley/magic-bus).)

## What is this?

_Magic Bus_ is an _internal_ message bus for JVM programs to talk with
themselves.
The bus uses type-inheritance of messages, not string topics, and
subscribers indicate interest in posted messages by type.
Methods are type-safe.

The goal is to support
[messaging patterns](https://www.enterpriseintegrationpatterns.com/patterns/messaging/)
within a single JVM program (process).
Gregor's
[EIP book](https://www.enterpriseintegrationpatterns.com/gregor.html) is an 
excellent resource for understanding messaging.

## Build

Use `./gradlew build` (Gradle) or `./batect build` (Batect) to build and run
tests.
CI (GitHub Actions) runs Batect on each non-README push.

## Terminology

- _Message_ (noun) &mdash; a JVM object of some instance type to be processed
  by subscribed functions or methods.
  Your code logic processes messages objects
- _Letter_ (noun) &mdash; synonym for "message". The nouns, "letter" and
  "message" mean the same, and are used depending on context.
  In code one talks about "messages"; "dead letter box" is an example of 
  using the synonym "letter"
- _Mailbox_ (noun) \[pl: mailboxen] &mdash; a function or method that receives
  messages and processes them in some fashion (possibly discarding them
  &mdash; though discarded messages might represent a _smell_ where further
  subtyping may be of benefit). 
  These typically manifest your business or processing logic, or provide 
  debugging, auditing, or operational features, _et al_.
  (The plural, "mailboxen", is fanciful, _cf_
  [_boxen_](http://catb.org/~esr/jargon/html/B/boxen.html)).
  See [`MagicBus.Mailbox`](./src/main/kotlin/hm/binkley/labs/MagicBus.kt)
- _Post_ (verb) &mdash; to send a message.
  The poster does not know which mailboxen may process the message.
  Your domain boundaries post messages for other internal domains to process,
  or communicate with external resources.
  See [`MessageBus.post`](./src/main/kotlin/hm/binkley/labs/MagicBus.kt)
- _Undelivered_ (adj) &mdash; a posted message with no subscribed mailbox to
  receive. This always indicates an error in program design or logic &mdash;
  you are posting messages for which there is no mailbox to receive.
  See the
  [`ReturnedMessage`](./src/main/kotlin/hm/binkley/labs/WithoutReceipt.kt)
  type in this library.
- _Failed_ (adj) &mdash; a "failed message" is when a mailbox raises a JVM
  exception while processing the message.
  When the exception is not handled by another mailbox, this indicates an 
  error in design or mailbox logic &mdash; when external input is bad, this 
  should result in a message processed by your program, not in crashing.
  See the
  [`FailedMessage`](./src/main/kotlin/hm/binkley/labs/WithoutReceipt.kt) type
  in this library.
- _Return receipt_ (noun) &mdash; a notification sent on succeful delivery 
  of messages when there are subscribers for these
  See the
  [`ReturnReceipt`](./src/main/kotlin/hm/binkley/labs/WithoutReceipt.kt) type
  in this library.

## Examples

### Post messages

Any JVM type can be published as a message.
A receiver (a receiver/subscriber is termed a `Mailbox` in code) receives 
new posts based on typing.

```kotlin
val bus: MagicBus // assigned elsewhere

bus.post(UUID.randomUUID()) // Only received by subscribers of `UUID` JDK type
UUID.randomUUID() postTo bus // fluent alternative
```

### Receive messages

Subscribe to message types explicitly (no annotations).

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

A similar example using Kotlin objects (could be a class instance):

```kotlin
val bus: MagicBus // assigned elsewhere

bus.subscribe(object : Mailbox<Number> {
    // `invoke` is Kotlin-specific; the technique varies by source language
    override fun invoke(message: Number) {
        println("I AM A NUMBER! HEAR MY VALUE: $message")
    }
})
```

### Process all messages regardless of type

```kotlin
val bus: MagicBus // assigned elsewhere

// Example useful in debugging
bus.subscribe<Any> { message ->
    myDebugLog.log(message) // Messages are never null 
}
```

### Stop receiving messages

```kotlin
val bus: MagicBus // assigned elsewhere

val mailbox: Mailbox<SomeType> = { println(it) }
bus.subscribe(mailbox) // Start receiving messages
mailbox subscribeTo bus // fluent alternative

bus.post(SomeType()) // PRINTED

bus.unsubscribe(mailbox) // Stop receiving messages
mailbox unscribeFrom bus // flient alternative

SomeType() postTo bus // NOT PRINTED
```

### Process dead letters or failed posts

See [_Make me a bus_](#make-me-a-bus) (next section) for an example that saves
dead letters or failed posts.
The key point is:

```kotlin
val deadLetters = Mailbox<DeadLetter> = { /* do something with it */ }
bus.subscribe(deadLetters)
val failedMessages = Mailbox<FailedMessage> = { /* do something with it */ }
bus.subscribe(failedMessages)
```

### Make me a bus

Use `MagicBusKt.CURRENT_THREAD_BUS` for a global single-threaded bus that by
default discards `ReturnedMessage` and `FailedMessage` posts.  
See `TestMagicBus` in
[`SimpleMagicBusTest`](./src/test/kotlin/hm/binkley/labs/SimpleMagicBusTest.kt)
as an example that tracks all posts for testing (the good, the bad, and the
ugly).

Programs should add handling for `ReturnedMessage` (no subscriber) and
`FailedMessage` (subscriber raised an unhandled exception).
The `SimpleMessageBus` class by default discards these message types: sensible
strategies include business logic and/or logging.

```kotlin
// Tracks the returned and failed messages -- debugging or testing
// Production code might fail and/or log: these are usually bugs
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

An alternative using a class extension:

```kotlin
class ExampleRecordingMagicBus : SimpleMagicBus() {
    private val _returned = mutableListOf<ReturnedMessage>()
    private val _failed = mutableListOf<FailedMessage>()

    // Typical Kotlin pattern for visible but not modifiable  
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

### Introspection

You may examine all mappings of message types to subscribers using
`MagicBus.subscribers`.
This may be useful in testing or debugging.
Mappings represent _internal state_ of your message bus, so be cautious in 
their use.

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

No attempt is made to detect unbounded loops and cycles.
So if you repost from within a mailbox, take care that the mailbox itself 
does not receive its reposting (including subtypes), or that if it 
self-receives, it includes properties in the message to terminate the cycle: 
otherwise you will eventually see a `StackOverflowError` thrown.

Detecting when reposting might terminate is an NP-hard problem akin to
[the halting problem](https://en.wikipedia.org/wiki/Halting_problem).  
However, you might code a heuristic to check "stack depth" (how many
repostings there are), and as an aid to debugging, post a failure message when
things look bleak.

## Deeper dive

The self-messaging bus is an analog to normal function call and the call
stack.
Rather than callers directly invoking other functions, the bus is an
intermediary, decoupling caller from receiver: this is a key technique in
programming.
This is not dissimilar from function pointers in other languages, or from
["trampolines"](https://en.wikipedia.org/wiki/Trampoline_%28computing%29), or
in JDK languages of passing "lambdas" as method parameters.
Functional programming relies on this decoupling.
One famous example is the mysteriously named
["Y combinator"](https://en.wikipedia.org/wiki/Y_Combinator).

## TODO

* Should `CURRENT_THREAD_BUS` be a pre-defined global?
* Greater null-safety in declarations (`*` vs `T : Any`). See
  [_Difference between "*" and "Any" in Kotlin
  generics_](https://stackoverflow.com/a/40139892)
* Consider breaking `MagicBus` into more fine-grained interfaces (_eg_,
  posting separate from subscribing separate from inspecting). See
  [_Namespacing in
  Kotlin_](https://arturdryomov.dev/posts/namespacing-in-kotlin/)
  for a discussion of implementation
* Consider `+=` on a bus to post a message rather than subscribe
