package org.unitedsoftware.mosaic

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.io.encoding.Base64
import kotlinx.coroutines.channels.SendChannel

/** Puts text on the clipboard of the computer running the terminal. */
public interface Clipboard {
	/**
	 * Copies [text] to the clipboard, using the OSC 52 escape sequence once the next frame is
	 * rendered. Terminals may ignore it, or ask the user for permission first.
	 */
	public fun setText(text: String)
}

public val LocalClipboard: ProvidableCompositionLocal<Clipboard> = staticCompositionLocalOf {
	throw AssertionError("No Clipboard provided")
}

/** Shows desktop notifications through the terminal. */
public interface Notifier {
	/**
	 * Shows a notification with [title] and [body] once the next frame is rendered. Terminals
	 * supporting the Kitty notification protocol show both separately. Otherwise the OSC 9 escape
	 * sequence is used, which terminals like iTerm2, Ghostty, and WezTerm support. Other terminals
	 * ignore it.
	 */
	public fun notify(title: String, body: String = "")
}

public val LocalNotifier: ProvidableCompositionLocal<Notifier> = staticCompositionLocalOf {
	throw AssertionError("No Notifier provided")
}

internal class CommandClipboard(
	private val commands: SendChannel<String>,
	private val onCommand: () -> Unit,
) : Clipboard {
	override fun setText(text: String) {
		commands.trySend("${OSC}52;c;${Base64.encode(text.encodeToByteArray())}$ST")
		onCommand()
	}
}

internal class CommandNotifier(
	private val kittyNotifications: Boolean,
	private val commands: SendChannel<String>,
	private val onCommand: () -> Unit,
) : Notifier {
	private var nextId = 1

	override fun notify(title: String, body: String) {
		val command = if (kittyNotifications) {
			val id = nextId++
			// Without "d=0" the notification is shown right away, before the body arrives.
			val titlePart = "${OSC}99;i=$id:d=${if (body.isEmpty()) 1 else 0};${title.sanitized()}$ST"
			if (body.isEmpty()) titlePart else titlePart + "${OSC}99;i=$id:p=body;${body.sanitized()}$ST"
		} else {
			val text = if (body.isEmpty()) title else "$title: $body"
			"${OSC}9;${text.sanitized()}$ST"
		}
		commands.trySend(command)
		onCommand()
	}

	/** Control characters, including the string terminator, would end the sequence early. */
	private fun String.sanitized() = filterNot { it.isISOControl() }
}
