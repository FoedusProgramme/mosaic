package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.PointerEventType
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onClick
import org.unitedsoftware.mosaic.layout.onHover
import org.unitedsoftware.mosaic.layout.onPointerEvent
import org.unitedsoftware.mosaic.layout.onPreviewKeyEvent
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Alignment
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Popup
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextField
import org.unitedsoftware.mosaic.ui.TextFieldState
import org.unitedsoftware.mosaic.ui.TextStyle

private val commands = listOf(
	"/clear" to "Clear the conversation",
	"/compact" to "Summarize the conversation so far",
	"/config" to "Open the settings",
	"/cost" to "Show what this session cost",
	"/help" to "List the commands",
	"/model" to "Switch the model",
	"/review" to "Review the current branch",
)

@Composable
fun CompletionPage() {
	val palette = LocalPalette.current
	val input = remember { TextFieldState() }
	var selected by remember { mutableIntStateOf(0) }
	var ran by remember { mutableStateOf("nothing yet") }
	val matches = if (input.text.startsWith("/") && ' ' !in input.text) {
		commands.filter { it.first.startsWith(input.text) }
	} else {
		emptyList()
	}
	if (selected > matches.lastIndex) selected = 0

	Column(
		// While the menu is open, it takes the keys before the text field and the catalog get them.
		Modifier.onPreviewKeyEvent { event ->
			if (matches.isEmpty()) return@onPreviewKeyEvent false
			when (event) {
				KeyEvent("ArrowUp") -> selected = (selected - 1 + matches.size) % matches.size
				KeyEvent("ArrowDown") -> selected = (selected + 1) % matches.size
				KeyEvent("Tab") -> input.text = matches[selected].first + " "
				KeyEvent("Escape") -> input.clear()
				else -> return@onPreviewKeyEvent false
			}
			true
		},
	) {
		Text("The menu is a Popup: it takes no space and draws on top", color = palette.muted)
		Text("of the text below it, extending up from the field.", color = palette.muted)
		Spacer(Modifier.height(1))
		Text("Last command: $ran", color = palette.good)
		Text("Some content which the menu covers while it is open.", color = palette.muted)
		Text("More content which the menu covers while it is open.", color = palette.muted)
		Text("Yet more content below the menu's anchor point.", color = palette.muted)
		Spacer(Modifier.height(1))
		if (matches.isNotEmpty()) {
			Popup(alignment = Alignment.BottomStart) {
				Column(
					// The wheel moves the selection like the arrow keys.
					Modifier.background(palette.surface).onPointerEvent {
						if (it.type != PointerEventType.Scroll) return@onPointerEvent false
						selected = (selected + it.scrollDelta).coerceIn(0, matches.lastIndex)
						true
					},
				) {
					matches.forEachIndexed { index, (name, description) ->
						val isSelected = index == selected
						// Hovering selects a command, and clicking completes it like Tab.
						val row = Modifier
							.onHover { if (it) selected = index }
							.onClick { input.text = "$name " }
						Row(if (isSelected) row.background(palette.accentContainer) else row) {
							Text(
								name.padEnd(10),
								color = if (isSelected) palette.accent else palette.text,
								textStyle = if (isSelected) TextStyle.Bold else TextStyle.Unspecified,
							)
							Text(description, color = palette.muted)
						}
					}
				}
			}
		}
		Row {
			Text("> ", color = palette.accent)
			TextField(
				input,
				modifier = Modifier.width(40),
				singleLine = true,
				placeholder = "Type / for commands",
				onSubmit = {
					if (it.isNotBlank()) ran = it.trim()
					input.clear()
				},
			)
		}
	}
}
