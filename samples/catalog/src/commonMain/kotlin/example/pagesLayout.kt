package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.layout.IntrinsicSize
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.aspectRatio
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.fillMaxHeight
import org.unitedsoftware.mosaic.layout.fillMaxSize
import org.unitedsoftware.mosaic.layout.fillMaxWidth
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.offset
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.layout.widthIn
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Alignment
import org.unitedsoftware.mosaic.ui.Arrangement
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Layout
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.unit.Constraints

/** Calls [onChange] with -[step] for ← and +[step] for →. */
fun Modifier.onLeftRight(step: Int = 1, onChange: (delta: Int) -> Unit): Modifier = onKeyEvent {
	when (it) {
		KeyEvent("ArrowLeft") -> onChange(-step)
		KeyEvent("ArrowRight") -> onChange(step)
		else -> return@onKeyEvent false
	}
	true
}

@Composable
private fun Heading(text: String) {
	Text(text, textStyle = TextStyle.Bold)
}

private class ArrangementOption(
	val name: String,
	val horizontal: Arrangement.Horizontal,
	val vertical: Arrangement.Vertical,
)

private val arrangements = listOf(
	ArrangementOption("Start / Top", Arrangement.Start, Arrangement.Top),
	ArrangementOption("Center", Arrangement.Center, Arrangement.Center),
	ArrangementOption("End / Bottom", Arrangement.End, Arrangement.Bottom),
	ArrangementOption("SpaceBetween", Arrangement.SpaceBetween, Arrangement.SpaceBetween),
	ArrangementOption("SpaceAround", Arrangement.SpaceAround, Arrangement.SpaceAround),
	ArrangementOption("SpaceEvenly", Arrangement.SpaceEvenly, Arrangement.SpaceEvenly),
	ArrangementOption("spacedBy(2)", Arrangement.spacedBy(2), Arrangement.spacedBy(2)),
)

@Composable
fun ArrangementPage() {
	val palette = LocalPalette.current
	var index by remember { mutableIntStateOf(3) }
	val option = arrangements[index]
	Column(Modifier.onLeftRight { index = (index + it + arrangements.size) % arrangements.size }) {
		Heading("Row(horizontalArrangement = …)")
		Panel {
			Row(Modifier.width(44), horizontalArrangement = option.horizontal) {
				Chip("A")
				Chip("B")
				Chip("C")
			}
		}
		Spacer(Modifier.height(1))
		Row {
			Column {
				Heading("Column(…)")
				Panel {
					Column(
						modifier = Modifier.size(9, 9),
						verticalArrangement = option.vertical,
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Chip("A")
						Chip("B")
						Chip("C")
					}
				}
			}
			Spacer(Modifier.width(4))
			Column {
				Heading("Arrangement")
				arrangements.forEachIndexed { i, it ->
					if (i == index) {
						Text("▸ ${it.name}", color = palette.accent, textStyle = TextStyle.Bold)
					} else {
						Text("  ${it.name}", color = palette.muted)
					}
				}
			}
		}
	}
}

private val alignments = listOf(
	"TopStart" to Alignment.TopStart,
	"TopCenter" to Alignment.TopCenter,
	"TopEnd" to Alignment.TopEnd,
	"CenterStart" to Alignment.CenterStart,
	"Center" to Alignment.Center,
	"CenterEnd" to Alignment.CenterEnd,
	"BottomStart" to Alignment.BottomStart,
	"BottomCenter" to Alignment.BottomCenter,
	"BottomEnd" to Alignment.BottomEnd,
)

@Composable
fun AlignmentPage() {
	val palette = LocalPalette.current
	Column {
		for (row in alignments.chunked(3)) {
			Row {
				for ((name, alignment) in row) {
					Panel(modifier = Modifier.size(17, 5), title = name) {
						Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
							Text("●", color = palette.accent)
						}
					}
				}
			}
		}
	}
}

@Composable
fun ModifiersPage() {
	val palette = LocalPalette.current
	Column {
		Row {
			Column {
				Heading(".background().padding()")
				Text("content", modifier = Modifier.background(palette.accentContainer).padding(horizontal = 2, vertical = 1))
			}
			Spacer(Modifier.width(3))
			Column {
				Heading(".padding().background()")
				Text("content", modifier = Modifier.padding(horizontal = 2, vertical = 1).background(palette.accentContainer))
			}
		}
		Spacer(Modifier.height(1))
		Row {
			Column {
				Heading(".offset(x = 4, y = 1)")
				Panel(modifier = Modifier.size(22, 5)) {
					Text("moved", modifier = Modifier.offset(x = 4, y = 1), background = palette.accentContainer)
				}
			}
			Spacer(Modifier.width(3))
			Column {
				Heading(".width(12).aspectRatio(3f)")
				Box(
					modifier = Modifier.width(12).aspectRatio(3f).background(palette.accentContainer),
					contentAlignment = Alignment.Center,
				) {
					Text("12 × 4")
				}
			}
		}
		Spacer(Modifier.height(1))
		Heading(".drawBehind { … }.padding(1)  (see Panels)")
		Text("a border is just drawing", modifier = Modifier.border(color = palette.accent))
	}
}

@Composable
fun SizeWeightPage() {
	val palette = LocalPalette.current
	var width by remember { mutableIntStateOf(40) }
	Column(Modifier.onLeftRight(step = 2) { width = (width + it).coerceIn(16, 50) }) {
		Text("Container width: $width", color = palette.accent)
		Spacer(Modifier.height(1))
		Heading(".weight(1f)  .weight(2f)  .weight(1f)")
		Row(Modifier.width(width)) {
			Box(Modifier.weight(1f).background(palette.accentContainer)) { Text("1") }
			Box(Modifier.weight(2f).background(palette.accent)) { Text("2", color = palette.surface) }
			Box(Modifier.weight(1f).background(palette.accentContainer)) { Text("1") }
		}
		Spacer(Modifier.height(1))
		Heading("Spacer(Modifier.weight(1f))")
		Row(Modifier.width(width).background(palette.surface)) {
			Text("start")
			Spacer(Modifier.weight(1f))
			Text("end")
		}
		Spacer(Modifier.height(1))
		Heading(".fillMaxWidth(fraction)")
		for (fraction in listOf(0.25f, 0.5f, 0.75f, 1f)) {
			Box(Modifier.width(width)) {
				Box(Modifier.fillMaxWidth(fraction).background(palette.accentContainer)) {
					Text("$fraction")
				}
			}
		}
		Spacer(Modifier.height(1))
		Heading(".widthIn(min = 12)")
		Box(Modifier.widthIn(min = 12).background(palette.accentContainer)) { Text("short") }
	}
}

private val menu = listOf("Open", "Save as…", "Export to PDF", "Quit")

@Composable
fun IntrinsicsPage() {
	val palette = LocalPalette.current
	Column {
		Row {
			Column {
				Heading("Column { … }")
				Spacer(Modifier.height(1))
				Column {
					for (item in menu) {
						Text(" $item ", background = palette.accentContainer)
					}
				}
			}
			Spacer(Modifier.width(4))
			Column {
				Heading(".width(IntrinsicSize.Max)")
				Spacer(Modifier.height(1))
				Column(Modifier.width(IntrinsicSize.Max)) {
					for (item in menu) {
						Box(Modifier.fillMaxWidth().background(palette.accentContainer)) {
							Text(" $item ")
						}
					}
				}
			}
		}
		Spacer(Modifier.height(1))
		Heading("Row(Modifier.height(IntrinsicSize.Min))")
		Text("A divider fills to the tallest sibling:", color = palette.muted)
		Spacer(Modifier.height(1))
		Row(Modifier.height(IntrinsicSize.Min)) {
			Text("one line")
			Filler('│', modifier = Modifier.width(3).fillMaxHeight().padding(horizontal = 1), foreground = palette.accent)
			Text("three\nlines\nhere")
			Filler('│', modifier = Modifier.width(3).fillMaxHeight().padding(horizontal = 1), foreground = palette.accent)
			Text("two\nlines")
		}
	}
}

/** Places children left to right, wrapping to a new line when the next one would not fit. */
@Composable
fun FlowRow(
	modifier: Modifier = Modifier,
	horizontalSpacing: Int = 1,
	verticalSpacing: Int = 0,
	content: @Composable () -> Unit,
) {
	Layout(content, modifier, debugInfo = { "FlowRow()" }) { measurables, constraints ->
		val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
		val positions = ArrayList<Pair<Int, Int>>(placeables.size)
		var x = 0
		var y = 0
		var lineHeight = 0
		var width = 0
		for (placeable in placeables) {
			if (x > 0 && x + placeable.width > constraints.maxWidth) {
				x = 0
				y += lineHeight + verticalSpacing
				lineHeight = 0
			}
			positions += x to y
			width = maxOf(width, x + placeable.width)
			x += placeable.width + horizontalSpacing
			lineHeight = maxOf(lineHeight, placeable.height)
		}
		layout(maxOf(width, constraints.minWidth), maxOf(y + lineHeight, constraints.minHeight)) {
			placeables.forEachIndexed { index, placeable ->
				val (px, py) = positions[index]
				placeable.place(px, py)
			}
		}
	}
}

private val tags = listOf(
	"kotlin", "compose", "terminal", "tui", "multiplatform", "jvm", "native",
	"ansi", "layout", "animation", "keyboard", "unicode", "kitty", "sixel",
)

@Composable
fun CustomLayoutPage() {
	val palette = LocalPalette.current
	var width by remember { mutableIntStateOf(36) }
	Column(Modifier.onLeftRight(step = 2) { width = (width + it).coerceIn(20, 48) }) {
		Text("Container width: $width", color = palette.accent)
		Spacer(Modifier.height(1))
		Panel(modifier = Modifier.width(width + 2), title = "FlowRow") {
			FlowRow(modifier = Modifier.fillMaxWidth()) {
				tags.forEachIndexed { index, tag ->
					Chip(tag, selected = index % 5 == 0)
				}
			}
		}
		Spacer(Modifier.height(1))
		Text("Layout(content) { measurables, constraints ->", color = palette.muted)
		Text("  measure each, then layout(w, h) { place(x, y) }", color = palette.muted)
	}
}
