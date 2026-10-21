package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.Container
import org.unitedsoftware.mosaic.DumpSnapshots
import org.unitedsoftware.mosaic.NodeSnapshots
import org.unitedsoftware.mosaic.TestChar
import org.unitedsoftware.mosaic.TestFiller
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.size
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntSize

class FillerTest {
	private val bigConstraints = Constraints(maxWidth = 5000, maxHeight = 5000)

	@Test fun fillerFixed() = runTest {
		val width = 4
		val height = 6
		runMosaicTest {
			setContent {
				TestFiller(Modifier.size(width = width, height = height))
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|$TestChar$TestChar$TestChar$TestChar
				|$TestChar$TestChar$TestChar$TestChar
				|$TestChar$TestChar$TestChar$TestChar
				|$TestChar$TestChar$TestChar$TestChar
				|$TestChar$TestChar$TestChar$TestChar
				|$TestChar$TestChar$TestChar$TestChar
				""".trimMargin(),
			)
		}
	}

	@Test fun fillerFixedWithPadding() = runTest {
		val width = 4
		val height = 6
		runMosaicTest {
			setContent {
				TestFiller(Modifier.size(width = width, height = height).padding(1))
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				| $TestChar$TestChar
				| $TestChar$TestChar
				| $TestChar$TestChar
				| $TestChar$TestChar
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun fillerFixedSize() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 40
			val height = 71

			setContent {
				Container(constraints = bigConstraints) {
					TestFiller(Modifier.size(width = width, height = height))
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(width, height))
		}
	}

	@Test fun fillerFixedWithSmallerContainer() = runTest {
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
						TestFiller(Modifier.size(width = width, height = height))
					}
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(containerWidth, containerHeight))
		}
	}

	@Test fun fillerWidth() = runTest {
		runMosaicTest(NodeSnapshots) {
			val width = 71

			setContent {
				Container(constraints = bigConstraints) {
					TestFiller(Modifier.width(width))
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(width, 0))
		}
	}

	@Test fun fillerWidthWithSmallerContainer() = runTest {
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
						TestFiller(Modifier.width(width))
					}
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(containerWidth, 0))
		}
	}

	@Test fun fillerHeight() = runTest {
		runMosaicTest(NodeSnapshots) {
			val height = 7

			setContent {
				Container(constraints = bigConstraints) {
					TestFiller(Modifier.height(height))
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(0, height))
		}
	}

	@Test fun fillerHeightWithSmallerContainer() = runTest {
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
						TestFiller(Modifier.height(height))
					}
				}
			}

			val rootNode = awaitSnapshot()
			val fillerNode = rootNode.children[0].children[0].children[0]
			assertThat(fillerNode.size).isEqualTo(IntSize(0, containerHeight))
		}
	}

	@Test fun fillerDebug() = runTest {
		runMosaicTest(DumpSnapshots) {
			setContent {
				TestFiller()
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Filler('$TestChar') x=0 y=0 w=0 h=0 DrawBehind
				""".trimMargin(),
			)
		}
	}
}
