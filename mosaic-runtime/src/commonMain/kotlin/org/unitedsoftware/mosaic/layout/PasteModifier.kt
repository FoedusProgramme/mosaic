package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier

public interface PasteModifier : Modifier.Element {
	/**
	 * This function is called when a [PasteEvent] is received by this node, before its children.
	 * It gives ancestors of a focused component, like a text field, the chance to intercept the
	 * event. Return true to stop propagation of this event. If you return false, the event will be
	 * sent to this [PasteModifier]'s children, and then back up through [onPasteEvent].
	 */
	public fun onPrePasteEvent(event: PasteEvent): Boolean = false

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
): Modifier = this then PasteModifierElement(null, onPasteEvent)

/**
 * Adding this [modifier][Modifier] to the [modifier][Modifier] parameter of a component will allow
 * it to intercept pasted text before its children, like a text field, receive it. For example, to
 * insert a placeholder in place of a large paste.
 *
 * @param onPreviewPasteEvent This callback is invoked when text is pasted into the terminal,
 *   before the children of this node. Return true to stop propagation of this event. If you return
 *   false, the event will be sent to this [onPreviewPasteEvent]'s children, and if none of them
 *   consume it, back up to the root through the `onPasteEvent` callbacks.
 */
public fun Modifier.onPreviewPasteEvent(
	onPreviewPasteEvent: (event: PasteEvent) -> Boolean,
): Modifier = this then PasteModifierElement(onPreviewPasteEvent, null)

@Poko
private class PasteModifierElement(
	val onPreEvent: ((PasteEvent) -> Boolean)?,
	val onEvent: ((PasteEvent) -> Boolean)?,
) : PasteModifier {
	override fun onPrePasteEvent(event: PasteEvent) = onPreEvent?.invoke(event) ?: false
	override fun onPasteEvent(event: PasteEvent) = onEvent?.invoke(event) ?: false
}
