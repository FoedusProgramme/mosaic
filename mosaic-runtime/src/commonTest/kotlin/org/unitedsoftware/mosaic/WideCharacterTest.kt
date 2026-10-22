package org.unitedsoftware.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.offset
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text

class WideCharacterTest {
	@Test fun measuredInColumns() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				Column {
					Text("ABC")
					Text("A中B")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Column(arrangement=Arrangement#Top, alignment=Horizontal(bias=-1)) x=0 y=0 w=4 h=2
				|  Text("ABC") x=0 y=0 w=3 h=1 DrawBehind
				|  Text("A中B") x=0 y=1 w=4 h=1 DrawBehind
				""".trimMargin(),
			)
		}
	}

	@Test fun followingTextStartsAfterBothColumns() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				Row {
					Text("中文")
					Text("|")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Row(arrangement=Arrangement#Start, alignment=Vertical(bias=-1)) x=0 y=0 w=5 h=1
				|  Text("中文") x=0 y=0 w=4 h=1 DrawBehind
				|  Text("|") x=4 y=0 w=1 h=1 DrawBehind
				""".trimMargin(),
			)
		}
	}

	@Test fun rendersOnceWithoutContinuation() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("ABC")
					Text("A中B")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|ABC
				|A中B
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun overwritingHalfBlanksTheOtherHalf() = runTest {
		runMosaicTest {
			setContent {
				Box {
					Text("中文")
					// Covers the second half of 中 and the first half of 文.
					Text("ab", modifier = Modifier.offset(1, 0))
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(" ab")
		}
	}

	@Test fun noRoomInLastColumnBecomesSpace() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(3, 1).drawBehind { drawText(0, 0, "ab中") })
			}

			assertThat(awaitSnapshot()).isEqualTo("ab")
		}
	}

	@Test fun fillStepsOverBothColumns() = runTest {
		runMosaicTest {
			setContent {
				Filler('中', modifier = Modifier.size(5, 1))
			}

			assertThat(awaitSnapshot()).isEqualTo("中中")
		}
	}

	@Test fun combiningMarksStayWithTheirCharacter() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				Row {
					Text("é")
					Text("|")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Row(arrangement=Arrangement#Start, alignment=Vertical(bias=-1)) x=0 y=0 w=2 h=1
				|  Text("e${"́"}") x=0 y=0 w=1 h=1 DrawBehind
				|  Text("|") x=1 y=0 w=1 h=1 DrawBehind
				""".trimMargin(),
			)
		}
		runMosaicTest {
			setContent {
				Text("éx")
			}

			assertThat(awaitSnapshot()).isEqualTo("éx")
		}
	}
}
