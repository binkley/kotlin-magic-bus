package hm.binkley.labs

import lombok.Generated

/**
 * Subscribe to [ReturnedMessage] to find out about posts with no subscribed
 * mailboxen.
 */
@Generated // Lie to JaCoCo
data class ReturnedMessage<T : Any>(
    val bus: MagicBus,
    val message: T,
)
