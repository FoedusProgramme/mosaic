package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest

class ProgressBarTest {
	@Test fun fillsToAnEighthOfACell() = runTest {
		runMosaicTest {
			setContent {
				Column {
					ProgressBar(0f, Modifier.width(10))
					Text("|")
					ProgressBar(0.15f, Modifier.width(10))
					ProgressBar(0.5f, Modifier.width(10))
					ProgressBar(1f, Modifier.width(10))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				||
				|█▌
				|█████
				|██████████
				""".trimMargin(),
			)
		}
	}

	@Test fun progressOutsideOfItsRangeShowsTheNearestEnd() = runTest {
		runMosaicTest {
			setContent {
				Column {
					ProgressBar(2f, Modifier.width(4))
					ProgressBar(-1f, Modifier.width(4))
					Text("|")
					ProgressBar(Float.NaN, Modifier.width(4))
					Text("|")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|████
				|
				||
				|
				||
				""".trimMargin(),
			)
		}
	}

	@Test fun unsizedIsTheDefaultWidth() = runTest {
		runMosaicTest {
			setContent {
				ProgressBar(1f)
			}
			assertThat(awaitSnapshot()).isEqualTo("█".repeat(DefaultProgressBarWidth))
		}
	}

	@Test fun trackShowsThePartStillToFill() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiRendering(TestTerminal.Capabilities()))) {
			val snapshot = setContentAndSnapshot {
				ProgressBar(0.5f, Modifier.width(4), color = Color.Red, trackColor = Color.Blue)
			}
			// The filled half in red, and the whole bar on blue.
			assertThat(snapshot.contains("██")).isEqualTo(true)
			assertThat(snapshot.contains("38;2;255;0;0")).isEqualTo(true)
			assertThat(snapshot.contains("48;2;0;0;255")).isEqualTo(true)
		}
	}
}
