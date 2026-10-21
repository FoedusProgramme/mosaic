package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.jakewharton.mosaic.animation.LinearEasing
import com.jakewharton.mosaic.animation.animateFloat
import com.jakewharton.mosaic.animation.infiniteRepeatable
import com.jakewharton.mosaic.animation.rememberInfiniteTransition
import com.jakewharton.mosaic.animation.tween
import com.jakewharton.mosaic.layout.DrawStyle
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Filler
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SpacerFillerPage() {
	val palette = LocalPalette.current
	Column {
		Text("Filler(char) fills whatever size it is given", textStyle = TextStyle.Bold)
		Row {
			Filler('░', modifier = Modifier.size(12, 3), foreground = palette.accent)
			Spacer(Modifier.width(2))
			Filler('▒', modifier = Modifier.size(12, 3), foreground = palette.warn)
			Spacer(Modifier.width(2))
			Filler('▓', modifier = Modifier.size(12, 3), foreground = palette.good)
		}
		Spacer(Modifier.height(1))
		Text("…which makes it a horizontal rule", textStyle = TextStyle.Bold)
		Filler('─', modifier = Modifier.width(48).height(1), foreground = palette.muted)
		Filler('═', modifier = Modifier.width(48).height(1), foreground = palette.accent)
		Filler('·', modifier = Modifier.width(48).height(1), foreground = palette.muted)
		Spacer(Modifier.height(1))
		Text("Spacer(Modifier.width(6))", textStyle = TextStyle.Bold)
		Row {
			Chip("A")
			Spacer(Modifier.width(6))
			Chip("B")
		}
		Spacer(Modifier.height(1))
		Text("Spacer(Modifier.weight(1f)) in a 48-wide Row", textStyle = TextStyle.Bold)
		Row(Modifier.width(48).background(palette.surface)) {
			Chip("start")
			Spacer(Modifier.weight(1f))
			Chip("end")
		}
	}
}

@Composable
fun DrawScopePage() {
	val palette = LocalPalette.current
	val phase by rememberInfiniteTransition("wave").animateFloat(
		initialValue = 0f,
		targetValue = 2 * PI.toFloat(),
		animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
		label = "phase",
	)
	Column {
		Box(
			modifier = Modifier.size(50, 5).drawBehind {
				drawRect(' ', background = palette.accentContainer, size = IntSize(14, 5))
				drawText(2, 5, "Fill")
				drawRect('#', foreground = palette.accent, topLeft = IntOffset(18, 0), size = IntSize(14, 5), drawStyle = DrawStyle.Stroke(1))
				drawText(2, 21, "Stroke(1)")
				drawRect('░', foreground = palette.warn, topLeft = IntOffset(36, 0), size = IntSize(14, 5), drawStyle = DrawStyle.Stroke(2))
				drawText(2, 38, "Stroke(2)")
			},
		)
		Spacer(Modifier.height(1))
		Text("drawText per column, two pixels per cell (▀ ▄)", textStyle = TextStyle.Bold)
		Panel {
			Box(
				modifier = Modifier.size(48, 8).drawBehind {
					val pixels = height * 2
					for (x in 0 until width) {
						val value = sin(x * 4 * PI / width + phase)
						val y = ((1 - (value + 1) / 2) * (pixels - 1)).roundToInt()
						drawText(
							row = y / 2,
							column = x,
							string = if (y % 2 == 0) "▀" else "▄",
							foreground = hsv(x.toFloat() / width, 0.6f, 1f),
						)
					}
				},
			)
		}
	}
}
