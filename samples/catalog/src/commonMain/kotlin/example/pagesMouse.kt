package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.layout.PointerEventType
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.border
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onClick
import org.unitedsoftware.mosaic.layout.onHover
import org.unitedsoftware.mosaic.layout.onPointerEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.LazyColumn
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle

@Composable
private fun MouseButton(label: String, onClick: () -> Unit) {
	val palette = LocalPalette.current
	var hovered by remember { mutableStateOf(false) }
	Text(
		" $label ",
		modifier = Modifier
			.onHover { hovered = it }
			.onClick(onClick)
			.padding(right = 1),
		color = if (hovered) palette.surface else palette.text,
		background = if (hovered) palette.accent else palette.accentContainer,
	)
}

@Composable
fun MousePage() {
	val palette = LocalPalette.current
	var clicks by remember { mutableIntStateOf(0) }
	var last by remember { mutableStateOf("move the pointer over the canvas") }
	var dot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
	var trail by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
	Column {
		Row {
			MouseButton("Click me") { clicks++ }
			MouseButton("Reset") {
				clicks = 0
				trail = emptyList()
			}
			Text("clicks: $clicks", color = palette.good)
		}
		Spacer(Modifier.height(1))
		Row {
			Box(
				modifier = Modifier
					.size(30, 10)
					.onPointerEvent { event ->
						last = "${event.type} ${event.button} at ${event.x},${event.y}"
						when (event.type) {
							PointerEventType.Press, PointerEventType.Drag -> {
								dot = event.x to event.y
								trail = (trail + (event.x to event.y)).takeLast(200)
							}

							PointerEventType.Move -> dot = event.x to event.y

							PointerEventType.Exit -> dot = null

							else -> {}
						}
						true
					}
					// Drawn before the border, so in the same coordinates as the pointer events.
					.drawBehind {
						for ((x, y) in trail) drawText(y, x, "•", foreground = palette.accent)
						dot?.let { (x, y) -> drawText(y, x, "+", foreground = palette.warn, textStyle = TextStyle.Bold) }
					}
					.border(color = palette.muted, title = "Drag here"),
			) {}
			Spacer(Modifier.width(2))
			Column {
				Text("Wheel over this list", color = palette.muted)
				LazyColumn(modifier = Modifier.width(20).height(9).background(palette.surface)) {
					items(100) { Text(" Row ${it + 1}") }
				}
			}
		}
		Spacer(Modifier.height(1))
		Text(last, color = palette.accent)
		Text("Mouse events are on by default on the alternate screen.", color = palette.muted)
		Text("Drag over other text to select and copy it, or double", color = palette.muted)
		Text("and triple click for words and rows.", color = palette.muted)
	}
}
