package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.layout.FocusDirection
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.LocalFocusManager
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.onPreviewKeyEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextField
import org.unitedsoftware.mosaic.ui.TextFieldState
import org.unitedsoftware.mosaic.ui.TextStyle

private fun KeyEvent.describe(): String = buildString {
	if (ctrl) append("ctrl+")
	if (alt) append("alt+")
	if (shift) append("shift+")
	append(if (key == " ") "Space" else key)
}

@Composable
fun KeyboardPage() {
	val palette = LocalPalette.current
	var last by remember { mutableStateOf<KeyEvent?>(null) }
	var history by remember { mutableStateOf(emptyList<String>()) }
	var count by remember { mutableIntStateOf(0) }
	Column(
		Modifier.onPreviewKeyEvent { event ->
			last = event
			history = (history + event.describe()).takeLast(4)
			count++
			false
		},
	) {
		Text("Press any key. ↑↓ and q still work because this")
		Text("preview handler returns false (not consumed).", color = palette.muted)
		Spacer(Modifier.height(1))
		Panel(modifier = Modifier.width(40), title = "Last KeyEvent") {
			Column {
				Labeled("key", labelWidth = 12) {
					Text(last?.let { "\"${it.key}\"" } ?: "-", color = palette.accent, textStyle = TextStyle.Bold)
				}
				Labeled("modifiers", labelWidth = 12) {
					Row {
						Flag("ctrl", last?.ctrl == true)
						Flag("alt", last?.alt == true)
						Flag("shift", last?.shift == true)
					}
				}
				Labeled("count", labelWidth = 12) { Text("$count") }
			}
		}
		Spacer(Modifier.height(1))
		Text("History", textStyle = TextStyle.Bold)
		for (index in 0 until 4) {
			Text(history.getOrNull(index) ?: "", color = palette.muted)
		}
		Spacer(Modifier.height(1))
		Text("Terminals with the Kitty keyboard protocol report", color = palette.muted)
		Text("keys like Shift+Enter separately from Enter.", color = palette.muted)
	}
}

@Composable
private fun Flag(name: String, on: Boolean) {
	val palette = LocalPalette.current
	Text(
		" $name ",
		modifier = Modifier.padding(right = 1),
		color = if (on) palette.surface else palette.muted,
		background = if (on) palette.accent else Color.Unspecified,
	)
}

@Composable
fun TextInputPage() {
	val palette = LocalPalette.current
	val name = remember { TextFieldState() }
	val message = remember { TextFieldState() }
	val focusManager = LocalFocusManager.current
	var sent by remember { mutableStateOf(emptyList<String>()) }
	Column(
		// Tab moves the focus between the fields. The single line name field does not handle Enter,
		// so it reaches the column, which moves on to the message.
		Modifier.onKeyEvent { event ->
			event.key == "Enter" && focusManager.moveFocus(FocusDirection.Next)
		},
	) {
		Text("TextField wraps, scrolls, takes pastes and CJK input,", color = palette.muted)
		Text("and places the terminal cursor for input methods.", color = palette.muted)
		Spacer(Modifier.height(1))
		Labeled("Name", labelWidth = 9) {
			TextField(
				name,
				modifier = Modifier.width(30).background(palette.surface),
				singleLine = true,
				placeholder = "Ada Lovelace",
			)
		}
		Spacer(Modifier.height(1))
		Labeled("Message", labelWidth = 9) {
			TextField(
				message,
				modifier = Modifier.width(40).background(palette.surface),
				maxLines = 3,
				placeholder = "Say something",
				onSubmit = {
					if (it.isBlank()) return@TextField
					val from = name.text.ifEmpty { "?" }
					sent = (sent + "$from: ${it.replace('\n', ' ')}").takeLast(4)
					message.clear()
				},
			)
		}
		Spacer(Modifier.height(1))
		Text("tab focus · enter send · shift/alt+enter new line", color = palette.muted)
		Spacer(Modifier.height(1))
		Text("Sent", textStyle = TextStyle.Bold)
		for (index in 0 until 4) {
			Text(sent.getOrNull(index)?.take(48) ?: "", color = palette.good)
		}
	}
}
