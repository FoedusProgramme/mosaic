package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.fillMaxSize
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.rememberScrollState
import org.unitedsoftware.mosaic.layout.verticalScroll
import org.unitedsoftware.mosaic.markdown.Markdown
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Box

private val response = """
## Reading a file in Kotlin

Use **`readText`** for small files, or *stream* lines with `useLines` when the file is ~~small~~ large:

```kotlin
// Prints the lines which mention errors.
fun printErrors(path: String) = File(path).useLines { lines ->
    lines.filter { "error" in it }.forEach(::println)
}
```

| Function | Loads | Good for |
|:---------|:-----:|---------:|
| `readText` | all | small files |
| `useLines` | lazily | logs |

- [x] Handles any size
- [ ] Handles binary files, see the [docs](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io/)

> Close the file when done, which `useLines` does for you.

```diff
-val text = File(path).readText()
+File(path).useLines { it.count() }
```
""".trimIndent()

@Composable
fun MarkdownPage() {
	var run by remember { mutableIntStateOf(0) }
	var shown by remember { mutableStateOf("") }
	val scroll = rememberScrollState()
	LaunchedEffect(run) {
		shown = ""
		// Arrive a few characters at a time, like the tokens of a language model.
		var index = 0
		while (index < response.length) {
			index = (index + 3).coerceAtMost(response.length)
			shown = response.substring(0, index)
			scroll.scrollToEnd()
			delay(15)
		}
	}
	Box(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(scroll)
			.onKeyEvent {
				if (it != KeyEvent(" ")) return@onKeyEvent false
				run++
				true
			},
	) {
		Markdown(shown)
	}
}
