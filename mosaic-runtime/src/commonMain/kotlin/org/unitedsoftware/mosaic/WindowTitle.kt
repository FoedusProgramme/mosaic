package org.unitedsoftware.mosaic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Sets the title of the terminal window while this is in the composition.
 *
 * When several are in the composition, the one composed last, which is usually the most deeply
 * nested one, sets the title. When a [WindowTitle] leaves the composition, the title of the previous
 * one applies again. Once none are left, or the program ends, the title the window had before is
 * restored in terminals which support it.
 *
 * The [title] is passed to the terminal as is, so control characters in it are not filtered.
 */
@Composable
public fun WindowTitle(title: String) {
	val manager = LocalWindowTitleManager.current
	val entry = remember(manager) {
		manager.register(title)
	}
	SideEffect {
		entry.title = title
	}
	DisposableEffect(entry) {
		onDispose {
			entry.dispose()
		}
	}
}

internal val LocalWindowTitleManager = staticCompositionLocalOf<WindowTitleManager> {
	throw IllegalStateException("No WindowTitleManager provided")
}

internal class WindowTitleManager {
	private val entries = mutableStateListOf<Entry>()

	val title: String? get() = entries.lastOrNull()?.title

	fun register(title: String): Entry {
		return Entry(title).also(entries::add)
	}

	inner class Entry(initialTitle: String) {
		var title by mutableStateOf(initialTitle)

		fun dispose() {
			entries.remove(this)
		}
	}
}
