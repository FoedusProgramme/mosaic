package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onPreviewKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle

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
		Text("Caution: non-ASCII keys (e.g. CJK via an IME)", color = palette.warn)
		Text("currently crash Mosaic, see issue #972.", color = palette.warn)
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
	val email = remember { TextFieldState() }
	// 0 = name, 1 = email, 2 = submit button.
	var focus by remember { mutableIntStateOf(0) }
	var submitted by remember { mutableStateOf<String?>(null) }
	Column(
		Modifier.onKeyEvent { event ->
			when {
				event.key == "Tab" -> focus = (focus + if (event.shift) 2 else 1) % 3

				event.key == "Enter" && focus < 2 -> focus++

				event.key == "Enter" -> {
					submitted = "${name.text.ifEmpty { "?" }} <${email.text.ifEmpty { "?" }}>"
					name.clear()
					email.clear()
					focus = 0
				}

				focus == 0 -> return@onKeyEvent name.onKeyEvent(event)

				focus == 1 -> return@onKeyEvent email.onKeyEvent(event)

				else -> return@onKeyEvent false
			}
			true
		},
	) {
		Text("Mosaic has no focus system or text field, so this", color = palette.muted)
		Text("page keeps focus in state and routes keys itself.", color = palette.muted)
		Spacer(Modifier.height(1))
		Labeled("Name", labelWidth = 8) {
			TextField(name, focused = focus == 0, width = 32, placeholder = "Ada Lovelace")
		}
		Spacer(Modifier.height(1))
		Labeled("Email", labelWidth = 8) {
			TextField(email, focused = focus == 1, width = 32, placeholder = "ada@example.com")
		}
		Spacer(Modifier.height(1))
		Row {
			Spacer(Modifier.width(8))
			if (focus == 2) {
				Text(" Submit ", color = palette.surface, background = palette.accent, textStyle = TextStyle.Bold)
			} else {
				Text(" Submit ", background = palette.surface)
			}
		}
		Spacer(Modifier.height(1))
		Text(submitted?.let { "Submitted: $it".take(50) } ?: "", color = palette.good)
		Spacer(Modifier.height(1))
		Text("Edit with ←→, Home/End, Backspace, Delete, ctrl+u.", color = palette.muted)
		Text("Typed q/j/k go into the field: the focused field", color = palette.muted)
		Text("consumes keys before the app-level handler.", color = palette.muted)
	}
}
