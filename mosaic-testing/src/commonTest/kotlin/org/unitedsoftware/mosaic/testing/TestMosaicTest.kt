package org.unitedsoftware.mosaic.testing

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Strikethrough

class TestMosaicTest {
	@Test fun setContentAndSnapshot() = runTest {
		runMosaicTest {
			var number by mutableIntStateOf(0)
			val initial = setContentAndSnapshot {
				Text("The number is: $number")
				LaunchedEffect(Unit) {
					number = 1
				}
			}

			// Defer to allow effect to run.
			delay(10.milliseconds)

			assertThat(initial).isEqualTo("The number is: 0")
			assertThat(awaitSnapshot()).isEqualTo("The number is: 1")
		}
	}

	@Test fun setContentThenSnapshot() = runTest {
		runMosaicTest {
			var number by mutableIntStateOf(0)
			setContent {
				Text("The number is: $number")
				LaunchedEffect(Unit) {
					number = 1
				}
			}

			// Defer to allow effect to run.
			delay(10.milliseconds)

			assertThat(awaitSnapshot()).isEqualTo("The number is: 1")
		}
	}

	@Test fun noAnsiEscapesByDefault() = runTest {
		runMosaicTest {
			val one = setContentAndSnapshot {
				Text(
					buildAnnotatedString {
						withStyle(
							SpanStyle(
								textStyle = Strikethrough,
								color = Color.Red,
								background = Color.Green,
							),
						) {
							append("hello")
						}
					},
				)
			}
			assertThat(one).isEqualTo("hello")

			val two = setContentAndSnapshot {
				Text("world")
			}
			assertThat(two).isEqualTo("world")
		}
	}
}
