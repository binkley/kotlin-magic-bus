package hm.binkley.labs

import lombok.Generated

/**
 * Subscribe to [ReturnedMessage] to find out about posts with no subscribed
 * mailboxes.
 */
@Generated
data class ReturnedMessage<T : Any>(
    val bus: MagicBus,
    val message: T,
)
