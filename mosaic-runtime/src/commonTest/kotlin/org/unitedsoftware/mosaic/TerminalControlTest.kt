package org.unitedsoftware.mosaic

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.unit.Constraints

class TerminalControlTest {
	private val log = mutableListOf<String>()
	private val testTerminal = TestTerminal()

	private val terminal = object : Terminal by testTerminal {
		val suspended = CompletableDeferred<Unit>()

		override suspend fun <T> released(block: suspend () -> T): T {
			log += "released"
			return block().also { log += "reacquired" }
		}

		override val canSuspendProcess get() = true

		override fun suspendProcess() {
			log += "suspended"
			suspended.complete(Unit)
		}
	}

	private val output = object : Output {
		override fun write(text: String) {
			Regex("count \\d").find(text)?.let { log += it.value }
		}

		override suspend fun pause() {
			log += "pause"
		}

		override fun resume() {
			log += "resume"
		}
	}

	@Test fun releaseDrawsNothingUntilTakenOverAgain() = runTest {
		runMosaicComposition(terminal, AnsiRendering(terminal.capabilities), { Constraints() }, output) {
			var count by remember { mutableIntStateOf(0) }
			val control = LocalTerminalControl.current
			Text("count $count")
			LaunchedEffect(Unit) {
				delay(10.milliseconds)
				control.release {
					log += "block"
					count = 1
					Snapshot.sendApplyNotifications()
					// Frames continue meanwhile, but draw nothing.
					delay(100.milliseconds)
				}
				delay(10.milliseconds)
			}
		}
		assertThat(log).containsExactly(
			"count 0",
			"pause",
			"released",
			"block",
			"reacquired",
			"resume",
			"count 1",
		)
	}

	@Test fun unhandledCtrlZSuspendsTheProcess() = runTest {
		testTerminal.events.trySend(KeyboardEvent('z'.code, modifiers = ModifierCtrl))
		runMosaicComposition(terminal, AnsiRendering(terminal.capabilities), { Constraints() }, output) {
			Text("count 0")
			LaunchedEffect(Unit) {
				terminal.suspended.await()
				delay(10.milliseconds)
			}
		}
		assertThat(log).containsExactly(
			"count 0",
			"pause",
			"released",
			"suspended",
			"reacquired",
			"resume",
			"count 0",
		)
	}

	@Test fun suspendingIsNotSupportedByDefault() = runTest {
		var suspended = true
		runMosaicComposition(testTerminal, AnsiRendering(testTerminal.capabilities), { Constraints() }, output) {
			val control = LocalTerminalControl.current
			LaunchedEffect(Unit) {
				suspended = control.suspendProcess()
			}
		}
		assertThat(suspended).isFalse()
	}
}
