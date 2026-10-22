package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.io.bytestring.decodeToString
import org.unitedsoftware.mosaic.terminal.KeyboardEvent

class TtyTerminalReleaseTest {
	@Test fun releasedRestoresModesAndPausesReading() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Focus events are reset (i.e, not enabled), so they are enabled while not released.
		expect("$CSI?1004\$p", reply = "$CSI?1004;2\$y")
		expect("${CSI}5n", reply = "${CSI}0n")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal {
			val result = released {
				// Input is left for other programs while released.
				ptyWrite("a")
				delay(50.milliseconds)
				assertThat(events.tryReceive().getOrNull()).isNull()
				"done"
			}
			assertThat(result).isEqualTo("done")

			// Reading resumes afterwards. Input left over from while released is discarded when raw
			// mode is enabled again.
			ptyWrite("b")
			assertThat(events.receive()).isEqualTo(KeyboardEvent('b'.code))
		}.decodeToString()

		// Disabled for the other programs, enabled again, and disabled when closed.
		val toggles = Regex("\\?1004([hl])").findAll(teardown).map { it.groupValues[1] }.toList()
		assertThat(toggles).containsExactly("l", "h", "l")
	}
}
