package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.ESC
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.ST
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.AnsiLevel
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
		runMosaicTest(capabilities = TestTerminal.Capabilities(kittyGraphics = false, sixelGraphics = false)) {
			setContent {
				Image(bitmap, modifier = Modifier.width(4))
			}
			assertThat(awaitSnapshot()).isEqualTo("▀▀▄")
		}
	}

	@Test fun kittyGraphicsSendsTheImageAndDrawsPlaceholders() = runTest {
		val bitmap = ImageBitmap(2, 2, intArrayOf(red, blue, blue, red))
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var show by mutableStateOf(true)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				if (show) Image(bitmap, modifier = Modifier.size(2, 1))
			}
			val frame = awaitSnapshot()
			val id = Regex("""a=t,q=2,f=32,s=2,v=2,i=(\d+),m=0;""").find(frame)!!.groupValues[1].toInt()
			// The pixels as RGBA, sent once with a virtual placement of the image's size in cells.
			val data = Base64.encode(byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1, 0, 0, -1, -1, -1, 0, 0, -1))
			assertThat(frame).contains("$data$ST${ESC}_Ga=p,q=2,U=1,i=$id,p=1,c=2,r=1$ST")
			// Cells hold the placeholder with the row and column diacritics, colored with the ID.
			val color = "38;2;${id shr 16 and 0xFF};${id shr 8 and 0xFF};${id and 0xFF}"
			assertThat(frame).contains("$color")
			assertThat(frame).contains("\uDBFB\uDEEE\u0305\u0305\uDBFB\uDEEE\u0305\u030D")

			show = false
			assertThat(awaitSnapshot()).contains("${ESC}_Ga=d,q=2,d=I,i=$id$ST")
		}
	}

	@Test fun kittyGraphicsNeedsTrueColor() = runTest {
		val bitmap = ImageBitmap(1, 2, intArrayOf(red, blue))
		runMosaicTest(capabilities = TestTerminal.Capabilities(ansiLevel = AnsiLevel.ANSI256, sixelGraphics = false)) {
			setContent {
				Image(bitmap, modifier = Modifier.size(1, 1))
			}
			assertThat(awaitSnapshot()).isEqualTo("▀")
		}
	}
}
