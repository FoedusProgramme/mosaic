package org.unitedsoftware.mosaic

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Layout
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text

class DebugRenderingTest {
	private val timeSource = TestTimeSource()
	private val rendering = DebugRendering(
		capabilities = TestTerminal.Capabilities(),
		systemClock = timeSource,
	)

	@Test fun drawFailureStillRendersMeasuredAndPlacedNodes() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Row {
					Text("Hello ")
					Layout(modifier = Modifier.drawBehind { throw UnsupportedOperationException() }) {
						layout(5, 1)
					}
				}
			}

			assertFailure {
				awaitSnapshot()
			}.isInstanceOf<RuntimeException>()
				.message()
				.isNotNull()
				.containsMatch(
					"""
					|Failed
					|
					|NODES:
					|Row\(arrangement=Arrangement#Start, alignment=Vertical\(bias=-1\)\) x=0 y=0 w=11 h=1
					|  Text\("Hello "\) x=0 y=0 w=6 h=1 DrawBehind
					|  Layout\(\) x=6 y=0 w=5 h=1 DrawBehind
					|
					|OUTPUT:
					|(kotlin\.|java\.lang\.)?UnsupportedOperationException
					""".trimMargin().replaceLineEndingsWithCRLF().toRegex(),
				)
		}
	}

	@Test fun framesIncludeStatics() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("Hello")
				StaticEffect {
					Text("Static")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|NODES:
				|Text("Hello") x=0 y=0 w=5 h=1 DrawBehind
				|
				|STATIC:
				|Static
				|
				|OUTPUT:
				|Hello
				|
				""".trimMargin().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun framesAfterFirstHaveTimeHeader() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("Hello")
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|NODES:
				|Text("Hello") x=0 y=0 w=5 h=1 DrawBehind
				|
				|OUTPUT:
				|Hello
				|
				""".trimMargin().replaceLineEndingsWithCRLF(),
			)

			timeSource += 100.milliseconds
			setContent {
				Text("Hey!")
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ +100ms
				|NODES:
				|Text("Hey!") x=0 y=0 w=4 h=1 DrawBehind
				|
				|OUTPUT:
				|Hey!
				|
				""".trimMargin().replaceLineEndingsWithCRLF(),
			)
		}
	}
}
