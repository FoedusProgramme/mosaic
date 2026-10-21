package org.unitedsoftware.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Layout
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text

class LayoutTest {
	@Test fun layoutDebugInfo() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				Layout(
					content = {
						Text("Hi!")
						Text("Hey!")
					},
					debugInfo = { "Custom()" },
				) { _, _ ->
					layout(0, 0) {
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Custom() x=0 y=0 w=0 h=0
				|  Text("Hi!") x=0 y=0 w=0 h=0 DrawBehind
				|  Text("Hey!") x=0 y=0 w=0 h=0 DrawBehind
				""".trimMargin(),
			)
		}
	}

	@Test fun noMeasureNoDraw() = runTest {
		runMosaicTest {
			setContent {
				Layout(
					content = {
						Text("CCC")
						Text("BB")
						Text("A")
					},
					modifier = Modifier.background(Color.Red),
					measurePolicy = { _, _ -> layout(3, 1) {} },
				)
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|  $s
				""".trimMargin(),
			)
		}
	}

	@Test fun noPlacementOverlaps() = runTest {
		runMosaicTest {
			setContent {
				Layout({
					Text("CCC")
					Text("BB")
					Text("A")
				}) { measurables, constraints ->
					for (measurable in measurables) {
						measurable.measure(constraints)
					}
					layout(3, 1) {
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|ABC
				""".trimMargin(),
			)
		}
	}

	@Test fun placementWorks() = runTest {
		runMosaicTest {
			setContent {
				Layout({
					Text("CCC")
					Text("BB")
					Text("A")
				}) { measurables, constraints ->
					val (c, b, a) = measurables.map { it.measure(constraints) }
					layout(8, 3) {
						a.place(0, 2)
						b.place(2, 1)
						c.place(5, 0)
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|     CCC
				|  BB
				|A
				""".trimMargin(),
			)
		}
	}

	@Test fun canvasIsNotClipped() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Row {
						// Force the width of the canvas to be 6.
						Text("123456")
					}
					Row {
						Text("..")
						Layout(
							modifier = Modifier.drawBehind {
								repeat(4) { row ->
									drawText(row, 0, "XXXX")
								}
							},
						) {
							layout(2, 2)
						}
						Text(".")
					}
					Row {
						Text("...")
					}
					Row {
						Text(".....")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|123456
				|..XX.X
				|  XXXX
				|...XXX
				|.....X
				""".trimMargin(),
			)
		}
	}
}
