package hm.binkley.labs

import lombok.Generated

/**
 * Subscribe to [ReturnReceipt] to find when a message is delivered.
 * This is generally poor practice as it adds coupling between parts of the
 * system, however may be useful in cases such as
 * - Logging
 * - Debugging
 */
@Generated // Lie to JaCoCo
data class ReturnReceipt<T : Any>(
    val bus: MagicBus,
    val message: T,
) : WithoutReceipt
