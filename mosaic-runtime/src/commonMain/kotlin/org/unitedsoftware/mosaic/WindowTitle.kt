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
	ComposedValue(LocalWindowTitles.current, title)
}

internal val LocalWindowTitles = staticCompositionLocalOf<ComposedValues<String>> {
	throw IllegalStateException("No window titles provided")
}

/** Puts [value] into [values] while this is in the composition. */
@Composable
internal fun <T : Any> ComposedValue(values: ComposedValues<T>, value: T) {
	val entry = remember(values) {
		values.register(value)
	}
	SideEffect {
		entry.value = value
	}
	DisposableEffect(entry) {
		onDispose {
			entry.dispose()
		}
	}
}

/** Values set by composables, of which the one composed last applies. */
internal class ComposedValues<T : Any> {
	private val entries = mutableStateListOf<Entry>()

	val current: T? get() = entries.lastOrNull()?.value

	fun register(value: T): Entry {
		return Entry(value).also(entries::add)
	}

	inner class Entry(initialValue: T) {
		var value by mutableStateOf(initialValue)

		fun dispose() {
			entries.remove(this)
		}
	}
}
