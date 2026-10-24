package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.testing.runMosaicTest

class SpinnerTest {
	@Test fun showsEachFrameInTurnAndStartsOver() = runTest {
		val style = SpinnerStyle(listOf("a", "b", "c"), 100.milliseconds)
		runMosaicTest {
			setContent {
				Spinner(style = style)
			}
			for (frame in listOf("a", "b", "c", "a", "b")) {
				assertThat(awaitSnapshot()).isEqualTo(frame)
			}
		}
	}

	@Test fun isAsWideAsItsWidestFrame() = runTest {
		val style = SpinnerStyle(listOf(".", "..."), 100.milliseconds)
		runMosaicTest {
			setContent {
				Row {
					Spinner(style = style)
					Text("|")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(".  |")
			assertThat(awaitSnapshot()).isEqualTo("...|")
		}
	}
}
