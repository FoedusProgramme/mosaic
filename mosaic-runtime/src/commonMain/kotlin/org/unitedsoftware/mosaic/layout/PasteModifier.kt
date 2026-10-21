package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier

public interface PasteModifier : Modifier.Element {
	/**
	 * This function is called when a [PasteEvent] is received by this node, after its children had
	 * the chance to handle it. Return true to stop propagation of this event. If you return false,
	 * the event will be sent to this [PasteModifier]'s parent.
	 */
	public fun onPasteEvent(event: PasteEvent): Boolean
}

/**
 * Text pasted into the terminal.
 *
 * Paste events require a terminal which supports bracketed paste. Otherwise, and when no
 * [onPasteEvent] handler consumes the event, the pasted text is delivered as individual key events
 * instead, where each line break is an `Enter` key event.
 */
@[Immutable Poko]
public class PasteEvent(
	/** The pasted text, with line breaks as `\n`. */
	public val text: String,
)

/**
 * Adding this [modifier][Modifier] to the [modifier][Modifier] parameter of a component will allow
 * it to receive pasted text.
 *
 * @param onPasteEvent This callback is invoked when text is pasted into the terminal. Return true
 *   to stop propagation of this event. If you return false, the event will be sent to this
 *   [onPasteEvent]'s parent.
 */
public fun Modifier.onPasteEvent(
	onPasteEvent: (event: PasteEvent) -> Boolean,
): Modifier = this then PasteModifierElement(onPasteEvent)

private class PasteModifierElement(
	val onEvent: (PasteEvent) -> Boolean,
) : PasteModifier {
	override fun onPasteEvent(event: PasteEvent) = onEvent(event)
}
