package hm.binkley.labs

import lombok.Generated

/**
 * Subscribe to [UndeliveredMessage] to find out about posts with no subscribed
 * mailboxen.
 */
@Generated // Lie to JaCoCo
data class UndeliveredMessage<T : Any>(
    val bus: MagicBus,
    val message: T,
)
