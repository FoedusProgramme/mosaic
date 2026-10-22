package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.unit.Constraints

class ImageTest {
	private val red = 0xFFFF0000.toInt()
	private val blue = 0xFF0000FF.toInt()
	private val clear = 0

	@Test fun fitsToCellsAndConstraints() {
		val bitmap = ImageBitmap(80, 64, IntArray(80 * 64))
		val cell = 8.0 to 16.0
		assertThat(fit(bitmap, cell, Constraints())).isEqualTo(10 to 4)
		// Narrower constraints scale it down, keeping its aspect ratio.
		assertThat(fit(bitmap, cell, Constraints(maxWidth = 5))).isEqualTo(5 to 2)
		assertThat(fit(bitmap, cell, Constraints(maxHeight = 1))).isEqualTo(3 to 1)
	}

	@Test fun resampleAveragesAndWeightsByAlpha() {
		val bitmap = ImageBitmap(2, 2, intArrayOf(red, blue, clear, red))
		val result = bitmap.resample(1, 1)
		// Three of four pixels are opaque, two of them red.
		assertThat(result.toList()).containsExactly(0xBFAA0055.toInt())
	}

	@Test fun halfBlocks() = runTest {
		// Red over blue, then red over nothing, then nothing over blue, then nothing.
		val bitmap = ImageBitmap(4, 2, intArrayOf(red, red, clear, clear, blue, clear, blue, clear))
		runMosaicTest(capabilities = TestTerminal.Capabilities(kittyGraphics = false)) {
			setContent {
				Image(bitmap, modifier = Modifier.width(4))
			}
			assertThat(awaitSnapshot()).isEqualTo("▀▀▄")
		}
	}
}
