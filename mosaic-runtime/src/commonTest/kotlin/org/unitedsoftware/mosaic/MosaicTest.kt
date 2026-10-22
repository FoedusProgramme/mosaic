package org.unitedsoftware.mosaic

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isPositive
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.offset
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntOffset

class MosaicTest {
	@Test fun changeInCompositionPhase() = runTest {
		runMosaicTest {
			setContent {
				var offsetX by remember { mutableIntStateOf(0) }

				Box(modifier = Modifier.width(10)) {
					Filler(TestChar, modifier = Modifier.size(1).offset(offsetX, 0))
				}

				LaunchedEffect(Unit) {
					delay(100.milliseconds)
					offsetX = 5
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("$TestChar")
			assertThat(awaitSnapshot()).isEqualTo("     $TestChar")
		}
	}

	@Test fun changeInLayoutPhase() = runTest {
		runMosaicTest {
			setContent {
				var offsetX by remember { mutableIntStateOf(0) }

				Box(modifier = Modifier.width(10)) {
					Filler(TestChar, modifier = Modifier.size(1).offset { IntOffset(offsetX, 0) })
				}

				LaunchedEffect(Unit) {
					delay(100.milliseconds)
					offsetX = 5
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("$TestChar")
			assertThat(awaitSnapshot()).isEqualTo("     $TestChar")
		}
	}

	@Test fun changeInDrawPhase() = runTest {
		runMosaicTest {
			setContent {
				var drawAnother by remember { mutableStateOf(false) }

				Box(modifier = Modifier.width(10)) {
					Spacer(
						modifier = Modifier
							.size(1)
							.drawBehind {
								if (drawAnother) {
									drawText(0, 0, "${TestChar + 1}")
								} else {
									drawText(0, 0, "$TestChar")
								}
							},
					)
				}

				LaunchedEffect(Unit) {
					delay(100.milliseconds)
					drawAnother = true
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("$TestChar")
			assertThat(awaitSnapshot()).isEqualTo("${TestChar + 1}")
		}
	}

	@Test fun frameTimeChanges() = runTest {
		var frameTimeA = 0L
		var frameTimeB = 0L

		val terminal = TestTerminal()
		runMosaicComposition(
			rendering = AnsiRendering(terminal.capabilities),
			terminal = terminal,
			rootConstraints = { Constraints() },
		) {
			LaunchedEffect(Unit) {
				withFrameNanos { frameTimeNanos ->
					frameTimeA = frameTimeNanos
				}
				withFrameNanos { frameTimeNanos ->
					frameTimeB = frameTimeNanos
				}
			}
		}

		assertThat(frameTimeA).all {
			isPositive()
			isLessThan(frameTimeB)
		}
	}

	@Test fun lifecycleUpdatesWithTerminal() = runTest {
		runMosaicTest {
			setContent {
				val terminal = LocalTerminalState.current
				val lifecycle = LocalLifecycleOwner.current.lifecycle
				Text("${terminal.focused} ${lifecycle.currentState}")
			}
			assertThat(awaitSnapshot()).isEqualTo("true RESUMED")

			state.focused.value = false
			assertThat(awaitSnapshot()).isEqualTo("false STARTED")
		}
	}
}
