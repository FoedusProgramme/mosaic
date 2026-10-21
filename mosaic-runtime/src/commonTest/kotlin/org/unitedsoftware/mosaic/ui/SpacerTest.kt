package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.Container
import org.unitedsoftware.mosaic.DumpSnapshots
import org.unitedsoftware.mosaic.NodeSnapshots
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.testTag
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.nodeByTestTag
import org.unitedsoftware.mosaic.s
import org.unitedsoftware.mosaic.size
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntSize

class SpacerTest {
	private val bigConstraints = Constraints(maxWidth = 5000, maxHeight = 5000)

	@Test fun spacerFixed() = runTest {
		val width = 4
		val height = 6

		runMosaicTest {
			setContent {
				Spacer(Modifier.background(Color.Red).size(width = width, height = height))
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|   $s
				|   $s
				|   $s
				|   $s
				|   $s
				|   $s
				""".trimMargin(),
			)
		}
	}

	@Test fun spacerFixedSize() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 40
			val height = 71

			setContent {
				Container(constraints = bigConstraints) {
					Spacer(Modifier.size(width = width, height = height).spacerTestTag())
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(width, height))
		}
	}

	@Test fun spacerFixedWithSmallerContainer() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 40
			val height = 71

			val containerWidth = 5
			val containerHeight = 7

			setContent {
				Box {
					Container(
						constraints = Constraints(
							maxWidth = containerWidth,
							maxHeight = containerHeight,
						),
					) {
						Spacer(Modifier.size(width = width, height = height).spacerTestTag())
					}
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(containerWidth, containerHeight))
		}
	}

	@Test fun spacerWidth() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 71

			setContent {
				Container(constraints = bigConstraints) {
					Spacer(Modifier.width(width).spacerTestTag())
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(width, 0))
		}
	}

	@Test fun spacerWidthWithSmallerContainer() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 40

			val containerWidth = 5
			val containerHeight = 7

			setContent {
				Box {
					Container(
						constraints = Constraints(
							maxWidth = containerWidth,
							maxHeight = containerHeight,
						),
					) {
						Spacer(Modifier.width(width).spacerTestTag())
					}
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(containerWidth, 0))
		}
	}

	@Test fun spacerHeight() = runTest {
		runMosaicTest(NodeSnapshots) {
			val height = 7

			setContent {
				Container(constraints = bigConstraints) {
					Spacer(Modifier.height(height).spacerTestTag())
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(0, height))
		}
	}

	@Test fun spacerHeightWithSmallerContainer() = runTest {
		runMosaicTest(NodeSnapshots) {
			val height = 23

			val containerWidth = 5
			val containerHeight = 7

			setContent {
				Box {
					Container(
						constraints = Constraints(
							maxWidth = containerWidth,
							maxHeight = containerHeight,
						),
					) {
						Spacer(Modifier.height(height).spacerTestTag())
					}
				}
			}

			val spacerNode = awaitSnapshot().spacerByTestTag()
			assertThat(spacerNode.size).isEqualTo(IntSize(0, containerHeight))
		}
	}

	@Test fun spacerDebug() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				Spacer()
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Spacer() x=0 y=0 w=0 h=0
				""".trimMargin(),
			)
		}
	}
}

private const val SPACER_TEST_TAG = "spacer"

private fun Modifier.spacerTestTag(): Modifier = testTag(SPACER_TEST_TAG)

private fun MosaicNode.spacerByTestTag(): MosaicNode = nodeByTestTag(SPACER_TEST_TAG)
