package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.CSI
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.clearLine
import org.unitedsoftware.mosaic.cursorRestore
import org.unitedsoftware.mosaic.cursorSave
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest

class SixelTest {
	private val red = 0xFFFF0000.toInt()
	private val green = 0xFF00FF00.toInt()

	@Test fun encode() {
		// Red at the top left, green below it, and nothing in the second column.
		val pixels = IntArray(2 * 7).also {
			it[0] = red
			it[2] = green
			it[12] = red
		}
		val red = (5 * 7 + 0) * 6 + 0
		val green = (0 * 7 + 6) * 6 + 0
		assertThat(encodeSixel(pixels, 2, 7)).isEqualTo(
			"\u001BP0;1;0q\"1;1;2;7" +
				"#$green;2;0;100;0#$red;2;100;0;0" +
				// The first band has both colors, the second one only red.
				"#${green}A$#$red@-" +
				"#$red@-" +
				"\u001B\\",
		)
	}

	@Test fun runLengthEncoding() {
		val encoded = encodeSixel(IntArray(5) { red }, 5, 1)
		assertThat(encoded).contains("!5@")
	}

	@Test fun rendering() = runTest {
		val bitmap = ImageBitmap(1, 1, intArrayOf(red))
		val capabilities = TestTerminal.Capabilities(kittyGraphics = false)
		val rendering = AnsiRendering(capabilities)
		var label by mutableStateOf("a")
		var show by mutableStateOf(true)
		runMosaicTest(RenderingSnapshots(rendering), capabilities) {
			setContent {
				Row {
					Text(label)
					if (show) Image(bitmap, modifier = Modifier.size(2, 1))
				}
			}
			val first = awaitSnapshot()
			// The image goes over its cells after the row is written.
			assertThat(first).contains("a\r${CSI}2G$cursorSave\u001BP0;1;0q")
			assertThat(first).contains("\u001B\\$cursorRestore")

			label = "b"
			// Writing the row again erased the image, so it is drawn again.
			assertThat(awaitSnapshot()).contains("\r${clearLine}b\r${CSI}2G$cursorSave")

			show = false
			val removed = awaitSnapshot()
			assertThat(removed).doesNotContain(cursorSave)
			// The row is written again to erase the image.
			assertThat(removed).contains("\r${clearLine}b")
		}
	}
}
