package hm.binkley.labs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MagicBusTest {
    private val bus = CURRENT_THREAD_BUS

    /**
     * @todo This test makes JaCoCo happier that lazy init worked,
     *       essentially testing that Kotlin stdlib works :(
     */
    @Test
    fun `should have a global default bus`() {
        assertThat(bus).isSameAs(CURRENT_THREAD_BUS)
    }
}
