package org.unitedsoftware.mosaic

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.unitedsoftware.mosaic.NonInteractivePolicy.AssumeAndIgnore
import org.unitedsoftware.mosaic.NonInteractivePolicy.Exit
import org.unitedsoftware.mosaic.NonInteractivePolicy.Ignore
import org.unitedsoftware.mosaic.NonInteractivePolicy.Return
import org.unitedsoftware.mosaic.NonInteractivePolicy.Throw
import org.unitedsoftware.mosaic.terminal.AnsiLevel
import org.unitedsoftware.mosaic.terminal.Event
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.tty.Tty
import org.unitedsoftware.mosaic.tty.terminal.asTerminalIn

internal suspend fun withTerminal(
	onNonInteractive: NonInteractivePolicy,
	block: suspend (Terminal) -> Unit,
): Boolean = coroutineScope {
	val tty = if (onNonInteractive != AssumeAndIgnore) {
		Tty.tryBind()
			?: when (onNonInteractive) {
				Exit -> nonInteractiveExit()
				Throw -> throw IllegalStateException(NonInteractiveMessage)
				Return -> return@coroutineScope false
				Ignore -> null
				AssumeAndIgnore -> throw AssertionError()
			}
	} else {
		null
	}

	tty.use {
		val terminal = tty?.asTerminalIn(this) ?: NonInteractiveTerminal
		terminal.use { block(it) }
	}

	true
}

/** Behaviors when there is no interactive TTY. */
public enum class NonInteractivePolicy {
	/** Print a non-interactive message to stderr and exit the process with a non-zero code. */
	Exit,

	/** Throw an [IllegalStateException] with a non-interactive message. */
	Throw,

	/** Immediately return false. */
	Return,

	/** Continue running with a fake [Terminal] with default state, no capabilities, and no events. */
	Ignore,

	/** Assume the absence of a TTY without checking, and proceed using [Ignore]. */
	AssumeAndIgnore,
}

internal object NonInteractiveTerminal : Terminal, Terminal.State, Terminal.Capabilities {
	override val name: String? get() = null
	override val interactive: Boolean get() = false

	override val state: Terminal.State get() = this
	override val capabilities: Terminal.Capabilities get() = this
	override val events: ReceiveChannel<Event> = Channel<Event>().apply { close() }

	override val focused: StateFlow<Boolean> = MutableStateFlow(true)
	override val theme: StateFlow<Terminal.Theme> = MutableStateFlow(Terminal.Theme.Unknown)
	override val size: StateFlow<Terminal.Size> = MutableStateFlow(Terminal.Size.Default)

	override val ansiLevel: AnsiLevel get() = AnsiLevel.NONE
	override val cursorVisibility: Boolean get() = false
	override val focusEvents: Boolean get() = false
	override val inBandResizeEvents: Boolean get() = false
	override val kittyGraphics get() = false
	override val kittyKeyboard get() = false
	override val kittyNotifications get() = false
	override val kittyPointerShape get() = false
	override val kittyTextSizingScale get() = false
	override val kittyTextSizingWidth get() = false
	override val kittyUnderline get() = false
	override val sixelGraphics get() = false
	override val synchronizedOutput get() = false
	override val themeEvents: Boolean get() = false

	override fun close() {}
}

internal const val NonInteractiveMessage = "Unable to run in non-interactive mode."
