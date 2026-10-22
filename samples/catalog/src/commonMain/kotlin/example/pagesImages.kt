package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Image
import org.unitedsoftware.mosaic.ui.ImageBitmap
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text

/** A disc with a hue wheel, fading out towards its edge, on a transparent background. */
private fun colorWheel(size: Int): ImageBitmap {
	val pixels = IntArray(size * size)
	val center = (size - 1) / 2.0
	for (y in 0 until size) {
		for (x in 0 until size) {
			val dx = x - center
			val dy = y - center
			val distance = sqrt(dx * dx + dy * dy) / center
			if (distance > 1) continue
			val hue = (kotlin.math.atan2(dy, dx) + PI) / (2 * PI)
			fun channel(offset: Double) = ((cos((hue + offset) * 2 * PI) + 1) / 2 * 255).toInt()
			val alpha = ((1 - distance * distance) * 255).toInt().coerceIn(0, 255)
			pixels[y * size + x] = (alpha shl 24) or (channel(0.0) shl 16) or (channel(1 / 3.0) shl 8) or channel(2 / 3.0)
		}
	}
	return ImageBitmap(size, size, pixels)
}

/** Diagonal stripes, which show how sharp each way of drawing images is. */
private fun stripes(width: Int, height: Int): ImageBitmap {
	val pixels = IntArray(width * height) { index ->
		val x = index % width
		val y = index / width
		val on = (x + y) / 6 % 2 == 0
		val shade = (sin(x * PI / width) * 200).toInt() + 40
		if (on) 0xFF000000.toInt() or (shade shl 16) or (90 shl 8) or (255 - shade) else 0xFF202030.toInt()
	}
	return ImageBitmap(width, height, pixels)
}

@Composable
fun ImagesPage() {
	val palette = LocalPalette.current
	val wheel = remember { colorWheel(256) }
	val stripes = remember { stripes(320, 64) }
	Column {
		Row {
			Image(wheel, modifier = Modifier.width(20).height(10), contentDescription = "color wheel")
			Spacer(Modifier.width(2))
			Image(wheel, modifier = Modifier.width(10).height(5), contentDescription = "small color wheel")
		}
		Spacer(Modifier.height(1))
		Image(stripes, modifier = Modifier.width(48), contentDescription = "stripes")
		Spacer(Modifier.height(1))
		Text("Kitty graphics uses Unicode placeholders, so images move", color = palette.muted)
		Text("with the text. Sixel is used where reported, otherwise", color = palette.muted)
		Text("half blocks show two pixels per cell.", color = palette.muted)
	}
}
