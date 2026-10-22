package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.CompositionLocalProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.cursorRestore
import org.unitedsoftware.mosaic.cursorSave
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest

class InlineImageTest {
	@Test fun crc() {
		assertThat(crc32("IEND".encodeToByteArray(), 0, 4)).isEqualTo(0xAE426082.toInt())
	}

	@Test fun png() {
		val png = encodePng(intArrayOf(0x80FF0000.toInt()), 1, 1)
		val expected = listOf(
			// Signature.
			0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
			// IHDR: 1 by 1, 8 bit RGBA.
			0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
		)
		assertThat(png.take(expected.size).map { it.toInt() and 0xFF }).isEqualTo(expected)
		// The IDAT holds a zlib header, one final stored block of the filter byte and RGBA, and the
		// Adler-32 of that data.
		val idat = png.copyOfRange(37, 37 + 4 + 2 + 5 + 5 + 4).map { it.toInt() and 0xFF }
		assertThat(idat).containsExactly(
			0x49, 0x44, 0x41, 0x54,
			0x78, 0x01,
			1, 5, 0, 0xFA, 0xFF,
			0, 0xFF, 0, 0, 0x80,
			0x04, 0x81, 0x01, 0x80,
		)
		assertThat(png.takeLast(12).map { it.toInt() and 0xFF }).isEqualTo(
			listOf(0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82),
		)
	}

	@Test fun terminals() {
		assertThat(supportsInlineImages("iTerm2 3.7.3")).isTrue()
		assertThat(supportsInlineImages("WezTerm 20240203")).isTrue()
		assertThat(supportsInlineImages("kitty(0.43.1)")).isFalse()
	}

	@Test fun itermUsesInlineImages() = runTest {
		val bitmap = ImageBitmap(1, 1, intArrayOf(0xFFFF0000.toInt()))
		val capabilities = TestTerminal.Capabilities()
		val rendering = AnsiRendering(capabilities)
		runMosaicTest(RenderingSnapshots(rendering), capabilities) {
			setContent {
				CompositionLocalProvider(LocalTerminalName provides "iTerm2 3.7.3") {
					Image(bitmap, modifier = Modifier.size(2, 1))
				}
			}
			val frame = awaitSnapshot()
			val png = Base64.encode(encodePng(IntArray(16 * 16) { 0xFFFF0000.toInt() }, 16, 16))
			assertThat(frame).contains("$cursorSave\u001B]1337;File=inline=1;size=")
			assertThat(frame).contains(";width=2;height=1;preserveAspectRatio=1:$png\u0007$cursorRestore")
		}
	}
}
