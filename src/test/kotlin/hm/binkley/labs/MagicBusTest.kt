package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MagicBusTest {
    private val bus = DEFAULT_BUS

    @Test
    fun `should share global default bus with same instance`() {
        assertThat(bus).isSameAs(DEFAULT_BUS)
    }
}
