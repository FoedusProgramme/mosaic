package org.unitedsoftware.mosaic.tty.terminal

import com.jakewharton.finalization.withFinalizationHook
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.unitedsoftware.mosaic.terminal.AnsiLevel
import org.unitedsoftware.mosaic.terminal.CapabilityQueryEvent
import org.unitedsoftware.mosaic.terminal.CursorPositionEvent
import org.unitedsoftware.mosaic.terminal.DebugEvent
import org.unitedsoftware.mosaic.terminal.DecModeReportEvent
import org.unitedsoftware.mosaic.terminal.DecModeReportEvent.Setting
import org.unitedsoftware.mosaic.terminal.Event
import org.unitedsoftware.mosaic.terminal.FocusEvent
import org.unitedsoftware.mosaic.terminal.KittyGraphicsEvent
import org.unitedsoftware.mosaic.terminal.KittyKeyboardQueryEvent
import org.unitedsoftware.mosaic.terminal.KittyNotificationEvent
import org.unitedsoftware.mosaic.terminal.KittyPointerQueryEvent
import org.unitedsoftware.mosaic.terminal.OperatingStatusResponseEvent
import org.unitedsoftware.mosaic.terminal.PrimaryDeviceAttributesEvent
import org.unitedsoftware.mosaic.terminal.ResizeEvent
import org.unitedsoftware.mosaic.terminal.SystemThemeEvent
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.terminal.TerminalVersionEvent
import org.unitedsoftware.mosaic.tty.Tty

private class TtyTerminal(
	override val name: String?,
	override val state: Terminal.State,
	override val capabilities: Terminal.Capabilities,
	override val events: ReceiveChannel<Event>,
	private val closeJob: Job,
	private val releaser: Releaser,
) : Terminal {
	override val interactive get() = true
	override fun close() = closeJob.cancel()

	override suspend fun <T> released(block: suspend () -> T): T = releaser.released(block)

	override val canSuspendProcess get() = releaser.tty.canSuspendProcess

	override suspend fun suspendProcess(): Boolean {
		if (!canSuspendProcess) return false
		released { releaser.tty.suspendProcess() }
		return true
	}

	class State(
		override val focused: StateFlow<Boolean>,
		override val theme: StateFlow<Terminal.Theme>,
		override val size: StateFlow<Terminal.Size>,
	) : Terminal.State

	class Capabilities(
		override val ansiLevel: AnsiLevel,
		override val cursorVisibility: Boolean,
		override val focusEvents: Boolean,
		override val inBandResizeEvents: Boolean,
		override val kittyGraphics: Boolean,
		override val kittyKeyboard: Boolean,
		override val kittyNotifications: Boolean,
		override val kittyPointerShape: Boolean,
		override val kittyTextSizingScale: Boolean,
		override val kittyTextSizingWidth: Boolean,
		override val kittyUnderline: Boolean,
		override val sixelGraphics: Boolean,
		override val synchronizedOutput: Boolean,
		override val themeEvents: Boolean,
	) : Terminal.Capabilities
}

private const val StageDeviceAttributes = 3
private const val StageCapabilityQueries = 2
private const val StageDefaultQueries = 1
private const val StageNormalOperation = 0

public suspend fun Tty.asTerminalIn(
	scope: CoroutineScope,
	/** When true, each terminal event will be immediately followed by a matching [DebugEvent]. */
	emitDebugEvents: Boolean = false,
): Terminal {
	// Entering raw mode can fail, so perform it before any additional control sequences which change
	// settings. We also need to be in character mode to query capabilities with control sequences.
	enableRawMode()

	val focused = MutableStateFlow(true)
	val theme = MutableStateFlow(Terminal.Theme.Unknown)
	val size = MutableStateFlow(Terminal.Size.Default)

	val events = Channel<Event>(64, onBufferOverflow = DROP_OLDEST)

	setCallback(EventParserTtyCallback(focused, size, events, emitDebugEvents))

	// Each of these will become true when their respective feature is recognized by the terminal
	// and was not already configured to our desired setting. Revert each toggled setting on exit.
	var toggleBracketedPaste = false
	var bracketedPasteReported = false
	var deviceAttributesReported = false
	var toggleCursor = false
	var toggleFocus = false
	var toggleInBandResize = false
	var toggleKittyKeyboard = false
	var toggleSystemTheme = false

	// TODO The design of finalization hook fights us here. We don't need suspension.
	val interruptJob = scope.launch(Unconfined, start = UNDISPATCHED) {
		withFinalizationHook(
			hook = {
				setCallback(null)
				if (toggleKittyKeyboard) write(kittyKeyboardPop)
				if (toggleBracketedPaste) write(bracketedPasteDisable)
				if (toggleSystemTheme) write(systemThemeDisable)
				if (toggleInBandResize) write(inBandResizeDisable)
				if (toggleFocus) write(focusDisable)
				if (toggleCursor) write(cursorEnable)
				reset()
			},
			block = {
				try {
					awaitCancellation()
				} finally {
					// When cancelled (from signal or normally), wake up the reader parse loop so it can exit.
					interruptRead()
				}
			},
		)
	}

	write("${CSI}0c")
	val debugBootstrap = env("MOSAIC_TTY_TERMINAL_DEBUG") == "true"
	var stage = StageDeviceAttributes

	var cursorVisibility = false
	var focusEvents = false
	var inBandResizeEvents = false
	var sizeReported = false
	var kittyGraphics = false
	var sixelGraphics = false
	var kittyKeyboard = false
	var kittyNotifications = false
	var kittyPointerShape = false
	var kittyTextSizingScale = false
	var kittyTextSizingWidth = false
	var kittyTextSizingCursorPositionCount = 0
	var kittyTextSizingLastCursorPosition: CursorPositionEvent? = null
	var kittyUnderlines = false
	var synchronizedOutput = false
	var terminalName: String? = null
	var themeEvents = false

	val releaser = Releaser(this)
	val bootstrapDone = CompletableDeferred<Unit>()
	scope.launch(Dispatchers.IO) {
		val parser = EventParser(this@asTerminalIn)
		while (true) {
			val event = parser.next()
			if (event == null) {
				// Interrupted, which is either to close or for releasing the terminal for a while.
				if (!releaser.awaitReacquired()) break
				continue
			}
			if (stage != StageNormalOperation && debugBootstrap) {
				write("$event\r\n")
			}
			when (event) {
				is PrimaryDeviceAttributesEvent -> {
					if (stage == StageNormalOperation) continue
					deviceAttributesReported = true
					// Attribute 4 announces Sixel graphics.
					sixelGraphics = "4" in event.data.split(';')

					if (event.id == 1) {
						// VT100 terminals can't handle most of the other queries so just bail.
						stage = StageNormalOperation
						bootstrapDone.complete(Unit)
						continue
					}

					stage = StageCapabilityQueries
					write(
						"$CSI?$cursorMode\$p" +
							"$CSI?$focusMode\$p" +
							"$CSI?$synchronizedOutputMode\$p" +
							"$CSI?$systemThemeMode\$p" +
							"$CSI?$inBandResizeMode\$p" +
							"$CSI?$bracketedPasteMode\$p" +
							"$CSI?996n" + // Theme query
							"${APC}Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA$ST" + // Kitty graphics
							"$CSI?u" + // Kitty keyboard
							"${OSC}99;i=1:p=?$ST" + // Kitty notifications
							"${OSC}22;?__current__$ST" + // Kitty pointer shape
							"${CSI}6n${OSC}66;w=1; $ST${CSI}6n${OSC}66;s=2; $ST${CSI}6n\r" + // Kitty text sizing
							"$DCS+q5375$ST" + // Kitty underline ("Su")
							"$CSI>0q" + // Xterm version
							"${CSI}5n", // DSR (end marker)
					)
				}

				is DecModeReportEvent -> {
					if (stage != StageCapabilityQueries) continue

					when (event.mode) {
						cursorMode -> {
							cursorVisibility = event.setting.canBeChanged
							if (event.setting == Setting.Set) {
								toggleCursor = true
								write(cursorDisable)
							}
						}

						focusMode -> {
							if (event.setting.isSupported) {
								focusEvents = true
								toggleFocus = event.setting == Setting.Reset
								// Enabling focus notification (even if already set) _might_ trigger an initial
								// event. There is otherwise no explicit way to request the initial value.
								write(focusEnable)
							}
						}

						synchronizedOutputMode -> {
							synchronizedOutput = event.setting.canBeChanged
						}

						systemThemeMode -> {
							themeEvents = event.setting.isSupported
							if (event.setting == Setting.Reset) {
								toggleSystemTheme = true
								write(systemThemeEnable)
							}
						}

						bracketedPasteMode -> {
							bracketedPasteReported = true
							if (event.setting == Setting.Reset) {
								toggleBracketedPaste = true
								write(bracketedPasteEnable)
							}
						}

						inBandResizeMode -> {
							if (event.setting.isSupported) {
								inBandResizeEvents = true
								toggleInBandResize = event.setting == Setting.Reset
								// Enabling in-band resize (even if already set) should trigger an initial event.
								write(inBandResizeEnable)
							}
						}
					}
				}

				is OperatingStatusResponseEvent -> {
					if (stage == StageCapabilityQueries) {
						if (focusEvents or inBandResizeEvents) {
							// By enabling these modes (or by sending an explicit default value query after
							// enabling the mode) wait for a reply about the default with a second DSR.
							stage = StageDefaultQueries
							write("${CSI}5n")
						} else {
							stage = StageNormalOperation
							bootstrapDone.complete(Unit)
						}
					} else if (stage == StageDefaultQueries) {
						bootstrapDone.complete(Unit)
					}
				}

				is KittyKeyboardQueryEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyKeyboard = true
						// Allows distinguishing keys like Shift+Enter from Enter. Pushed onto the terminal's
						// stack of flags so that popping it on exit restores whatever was there before.
						toggleKittyKeyboard = true
						write(kittyKeyboardPushDisambiguate)
					}
				}

				is KittyGraphicsEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyGraphics = true
					}
				}

				is KittyPointerQueryEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyPointerShape = true
					}
				}

				is KittyNotificationEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyNotifications = true
					}
				}

				is CapabilityQueryEvent -> {
					if (stage == StageCapabilityQueries && event.success) {
						if ("Su" in event.data) {
							kittyUnderlines = true
						}
					}
				}

				is CursorPositionEvent -> {
					if (stage == StageCapabilityQueries) {
						when (kittyTextSizingCursorPositionCount++) {
							1 -> kittyTextSizingWidth = event != kittyTextSizingLastCursorPosition
							2 -> kittyTextSizingScale = event != kittyTextSizingLastCursorPosition
						}
						kittyTextSizingLastCursorPosition = event
					}
				}

				is TerminalVersionEvent -> {
					if (stage == StageCapabilityQueries) {
						terminalName = event.data
					}
				}

				is FocusEvent -> {
					if (focusEvents) {
						focused.value = event.focused
					} else {
						// TODO Report unsolicited focus events... somewhere.
					}
				}

				is ResizeEvent -> {
					if (inBandResizeEvents) {
						sizeReported = true
						size.value = Terminal.Size(event.columns, event.rows, event.width, event.height)
					} else {
						// TODO Report unsolicited resize events... somewhere.
					}
				}

				is SystemThemeEvent -> {
					if (themeEvents || stage == StageCapabilityQueries) {
						theme.value = if (event.isDark) {
							Terminal.Theme.Dark
						} else {
							Terminal.Theme.Light
						}
					} else {
						// TODO Report unsolicited theme events... somewhere.
					}
				}

				else -> {}
			}

			events.trySend(event)
		}
	}

	// Spend at most 1 second bootstrapping capabilities and defaults. In theory, there could
	// exist a terminal which does not respond to DA1 or DSR. Does that terminal actually work?
	// Who knows, but we don't want to hang forever waiting. Take whatever we got so far
	// (if anything) and move on with rendering.
	withTimeoutOrNull(1.seconds) {
		bootstrapDone.await()
	}
	stage = StageNormalOperation

	// Pasted text is delivered as a whole instead of as individual key presses with bracketed paste.
	// Some terminals which support it, like tmux which also identifies as a VT100, do not report
	// modes. Enabling it is harmless in terminals which do not support it, so only skip it when the
	// terminal said it is unsupported, or did not identify as a terminal at all.
	if (deviceAttributesReported && !bracketedPasteReported) {
		toggleBracketedPaste = true
		write(bracketedPasteEnable)
	}

	if (debugBootstrap) {
		write("\r\n")
	}

	// Not all terminals report their size when in-band resize events are enabled, like iTerm2, so
	// without a report the initial size comes from the TTY. Changes after enabling them arrive as
	// events.
	if (!sizeReported) {
		currentSize().let { (columns, rows, width, height) ->
			size.value = Terminal.Size(columns, rows, width, height)
		}
	}
	if (!inBandResizeEvents) {
		enableWindowResizeEvents()
	}

	val ansiLevel = detectAnsiLevel()

	return TtyTerminal(
		name = terminalName,
		state = TtyTerminal.State(
			focused = focused,
			theme = theme,
			size = size,
		),
		capabilities = TtyTerminal.Capabilities(
			ansiLevel = ansiLevel,
			cursorVisibility = cursorVisibility,
			focusEvents = focusEvents,
			inBandResizeEvents = inBandResizeEvents,
			kittyGraphics = kittyGraphics,
			kittyKeyboard = kittyKeyboard,
			kittyNotifications = kittyNotifications,
			kittyPointerShape = kittyPointerShape,
			kittyTextSizingScale = kittyTextSizingScale,
			kittyTextSizingWidth = kittyTextSizingWidth,
			kittyUnderline = kittyUnderlines,
			sixelGraphics = sixelGraphics,
			synchronizedOutput = synchronizedOutput,
			themeEvents = themeEvents,
		),
		events = events,
		closeJob = interruptJob,
		releaser = releaser.apply {
			onRelease = {
				if (toggleKittyKeyboard) write(kittyKeyboardPop)
				if (toggleBracketedPaste) write(bracketedPasteDisable)
				if (toggleSystemTheme) write(systemThemeDisable)
				if (toggleInBandResize) write(inBandResizeDisable)
				if (toggleFocus) write(focusDisable)
				if (toggleCursor) write(cursorEnable)
				reset()
			}
			onReacquire = {
				enableRawMode()
				if (toggleCursor) write(cursorDisable)
				if (toggleFocus) write(focusEnable)
				if (toggleInBandResize) write(inBandResizeEnable)
				if (toggleSystemTheme) write(systemThemeEnable)
				if (toggleBracketedPaste) write(bracketedPasteEnable)
				if (toggleKittyKeyboard) write(kittyKeyboardPushDisambiguate)
				// The window may have been resized meanwhile, without events being read.
				currentSize().let { (columns, rows, width, height) ->
					size.value = Terminal.Size(columns, rows, width, height)
				}
			}
		},
	)
}

/** Pauses reading events while the terminal is released, see [Terminal.released]. */
private class Releaser(val tty: Tty) {
	lateinit var onRelease: () -> Unit
	lateinit var onReacquire: () -> Unit

	private val mutex = Mutex()

	/** Set while released: completed by the reader once it paused, and when reading can resume. */
	private var paused: CompletableDeferred<Unit>? = null
	private var reacquired: CompletableDeferred<Unit>? = null

	suspend fun <T> released(block: suspend () -> T): T = mutex.withLock {
		val paused = CompletableDeferred<Unit>()
		val reacquired = CompletableDeferred<Unit>()
		this.paused = paused
		this.reacquired = reacquired
		tty.interruptRead()
		paused.await()
		onRelease()
		try {
			block()
		} finally {
			onReacquire()
			this.paused = null
			this.reacquired = null
			reacquired.complete(Unit)
		}
	}

	/** Called by the reader when interrupted. Returns false when it should stop instead. */
	suspend fun awaitReacquired(): Boolean {
		val reacquired = reacquired ?: return false
		paused?.complete(Unit)
		reacquired.await()
		return true
	}
}

private fun Tty.write(s: String) {
	val b = s.encodeToByteArray()
	var offset = 0
	while (offset < b.size) {
		val written = write(b, offset, b.size - offset)
		if (written == -1) throw EofException()
		offset += written
	}
}

internal fun detectAnsiLevel(): AnsiLevel {
	if (!env("NO_COLOR").isNullOrEmpty()) {
		return AnsiLevel.NONE
	}
	val term = env("COLORTERM") ?: env("TERM") ?: "dumb"
	if (term.contains("24bit", ignoreCase = true) || term.contains("truecolor", ignoreCase = true)) {
		return AnsiLevel.TRUECOLOR
	}
	if (term.contains("256")) {
		return AnsiLevel.ANSI256
	}
	if (term != "dumb") {
		return AnsiLevel.ANSI16
	}
	// https://github.com/microsoft/terminal/issues/1040#issuecomment-496691842
	if (env("WT_SESSION") != null) {
		return AnsiLevel.TRUECOLOR
	}
	// Per https://conemu.github.io/en/ConEmuEnvironment.html
	if (env("ConEmuANSI") == "ON") {
		return AnsiLevel.TRUECOLOR
	}
	return AnsiLevel.NONE
}

private val Setting.isSupported get() = when (this) {
	Setting.NotRecognized -> false
	Setting.Set -> true
	Setting.Reset -> true
	Setting.PermanentlySet -> true
	Setting.PermanentlyReset -> false
}

private val Setting.canBeChanged get() = when (this) {
	Setting.NotRecognized -> false
	Setting.Set -> true
	Setting.Reset -> true
	Setting.PermanentlySet -> false
	Setting.PermanentlyReset -> false
}
