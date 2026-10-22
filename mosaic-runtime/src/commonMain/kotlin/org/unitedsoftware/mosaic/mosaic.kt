package org.unitedsoftware.mosaic

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.unitedsoftware.mosaic.NonInteractivePolicy.Exit
import org.unitedsoftware.mosaic.layout.FocusOwner
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.LocalFocusManager
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.layout.PasteEvent
import org.unitedsoftware.mosaic.layout.PointerButton
import org.unitedsoftware.mosaic.layout.PointerDispatcher
import org.unitedsoftware.mosaic.layout.PointerEventType
import org.unitedsoftware.mosaic.terminal.BracketedPasteEvent
import org.unitedsoftware.mosaic.terminal.CursorPositionEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.MouseEvent
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.ui.BoxMeasurePolicy
import org.unitedsoftware.mosaic.ui.LocalPopupHost
import org.unitedsoftware.mosaic.ui.LocalTerminalCapabilities
import org.unitedsoftware.mosaic.ui.LocalTerminalCommands
import org.unitedsoftware.mosaic.ui.LocalTerminalName
import org.unitedsoftware.mosaic.ui.PopupHost
import org.unitedsoftware.mosaic.ui.unit.Constraints

/**
 * Run [content] until it completes, for use in `main`.
 *
 * @param alternateScreen When true, render full screen on the terminal's alternate screen, which is
 *   restored once [content] completes. The content is then measured to exactly the size of the
 *   terminal. Otherwise, render below the previous output, as wide as the terminal and at most as
 *   tall as it.
 * @param mouse Whether to report mouse events to modifiers like `onPointerEvent` and `onClick`,
 *   and scroll with the wheel. This takes over the mouse, so the terminal's own text selection and
 *   scrolling only work with a modifier key held, like Shift or Option, depending on the terminal.
 *   Instead, dragging where no pointer handler takes the press selects text, double clicks select
 *   words and triple clicks rows, and the selection is copied to the clipboard with OSC 52 once
 *   released. It is enabled on the alternate screen by default.
 */
public fun runMosaicMain(
	alternateScreen: Boolean = false,
	mouse: Boolean = alternateScreen,
	content: @Composable () -> Unit,
) {
	runMosaicBlocking(alternateScreen = alternateScreen, mouse = mouse, content = content)
}

/** @see runMosaicMain */
public fun runMosaicBlocking(
	onNonInteractive: NonInteractivePolicy = Exit,
	alternateScreen: Boolean = false,
	mouse: Boolean = alternateScreen,
	content: @Composable () -> Unit,
): Boolean {
	return runBlocking {
		runMosaic(onNonInteractive, alternateScreen, mouse, content)
	}
}

/** @see runMosaicMain */
public suspend fun runMosaic(
	onNonInteractive: NonInteractivePolicy = Exit,
	alternateScreen: Boolean = false,
	mouse: Boolean = alternateScreen,
	content: @Composable () -> Unit,
): Boolean = withTerminal(onNonInteractive) { terminal ->
	val terminalSize = { terminal.state.size.value }
	// Without a terminal, there are no mouse events.
	val mouse = mouse && terminal.interactive
	val rendering = when {
		env("MOSAIC_DEBUG_RENDERING") == "true" -> DebugRendering(terminal.capabilities)
		alternateScreen -> AlternateScreenRendering(terminal.capabilities, terminalSize, mouse)
		else -> AnsiRendering(terminal.capabilities, terminalSize, mouse)
	}
	val rootConstraints: (Terminal.Size) -> Constraints = if (alternateScreen) {
		{ Constraints.fixed(it.columns, it.rows) }
	} else {
		{ Constraints(maxWidth = it.columns) }
	}

	val output = if (terminal.interactive) InterceptingOutput.tryStart() ?: PrintOutput else PrintOutput
	val pointerMapping = rendering as? PointerMapping ?: PointerMapping.Identity
	runMosaicComposition(terminal, rendering, rootConstraints, output, pointerMapping, content)
}

internal suspend fun runMosaicComposition(
	terminal: Terminal,
	rendering: Rendering,
	rootConstraints: (Terminal.Size) -> Constraints,
	output: Output,
	pointerMapping: PointerMapping = PointerMapping.Identity,
	content: @Composable () -> Unit,
) {
	val clock = BroadcastFrameClock()
	val mosaicComposition = MosaicComposition(
		coroutineContext = coroutineContext + clock,
		onDraw = { rootNode ->
			output.write(rendering.render(rootNode).toString())
		},
		terminal = terminal,
		rootConstraints = rootConstraints,
		pointerMapping = pointerMapping,
		onRelease = {
			output.write(rendering.release().toString())
			output.pause()
		},
		onReacquire = {
			output.resume()
			rendering.reacquire()
		},
	)

	try {
		// Lines written to stdout and stderr by other means are shown above the display.
		output.startIntercepting(mosaicComposition::logStatic)
		mosaicComposition.setContent(content)

		mosaicComposition.scope.launch {
			while (true) {
				clock.sendFrame(nanoTime())

				// "1000 FPS should be enough for anybody"
				// We need to yield in order for other coroutines on this dispatcher to run, otherwise
				// this is effectively a spin loop. We do a delay instead of a yield since dispatchers
				// are not required to support yield, but reasonable delay support is almost a guarantee.
				delay(1)
			}
		}

		mosaicComposition.awaitComplete()
	} finally {
		output.write(rendering.finish().toString())
		output.stop()
	}
}

public interface Mosaic {
	public fun setContent(content: @Composable () -> Unit)

	public fun draw(): TextCanvas
	public fun static(): String?
	public fun dumpNodes(): String

	/** The title for the terminal window set with [WindowTitle], or null when none is. */
	public fun title(): String?

	/** The name of the pointer shape set with [WindowPointerShape], or null when none is. */
	public fun pointerShape(): String?

	/**
	 * Escape sequences requested since the last call which do not draw anything, such as copying to
	 * the clipboard or showing a notification, or null when there are none.
	 */
	public fun terminalCommands(): String?

	public suspend fun awaitComplete()
	public fun cancel()
}

public fun Mosaic(
	coroutineContext: CoroutineContext,
	onDraw: (Mosaic) -> Unit,
	terminal: Terminal,
): Mosaic {
	return MosaicComposition(coroutineContext, onDraw, terminal)
}

internal class MosaicComposition(
	coroutineContext: CoroutineContext,
	private val onDraw: (Mosaic) -> Unit,
	private val terminal: Terminal,
	/** The constraints of the root node for the size of the terminal. */
	private val rootConstraints: (Terminal.Size) -> Constraints = { Constraints() },
	/** Maps the rows of mouse events to rows of the output. */
	private val pointerMapping: PointerMapping = PointerMapping.Identity,
	/** Called before the terminal is released for other programs, see [TerminalControl.released]. */
	private val onRelease: suspend () -> Unit = {},
	/** Called once the terminal is taken over again, before the next frame. */
	private val onReacquire: () -> Unit = {},
) : Mosaic,
	LifecycleOwner {
	private val externalClock = checkNotNull(coroutineContext[MonotonicFrameClock]) {
		"Mosaic requires an external MonotonicFrameClock in its coroutine context"
	}
	private val internalClock = BroadcastFrameClock()

	private val job = Job(coroutineContext[Job])
	private val composeContext = coroutineContext + job + internalClock
	val scope = CoroutineScope(composeContext)

	private val applier = MosaicNodeApplier { needLayout = true }
	private val layoutInvalidator = { needLayout = true }
	val rootNode = applier.root
	private val popupHost = PopupHost()
	private val focusOwner = FocusOwner { listOf(rootNode) + popupHost.popups.map { it.root } }
	private val pointerDispatcher = PointerDispatcher({ listOf(rootNode) + popupHost.popups.map { it.root } }, focusOwner)
	private val recomposer = Recomposer(composeContext)
	private val composition = Composition(applier, recomposer)

	private val staticLogs = Channel<Any>(UNLIMITED)

	private val windowTitles = ComposedValues<String>()
	private val pointerShapes = ComposedValues<PointerShape>()
	private val commands = Channel<String>(UNLIMITED)
	private val clipboard = CommandClipboard(commands) { needDraw = true }
	private val terminalCommandSender: (String) -> Unit = { command ->
		commands.trySend(command)
		needDraw = true
	}
	private val notifier = CommandNotifier(terminal.capabilities.kittyNotifications, commands) { needDraw = true }
	private val staticLogger = StaticLogger(staticLogs) { needDraw = true }

	/** Runs the bookkeeping of releasing the terminal where frames are drawn, not concurrently. */
	private val frameContext = coroutineContext[ContinuationInterceptor] ?: EmptyCoroutineContext

	private val terminalControl = object : TerminalControl {
		private val mutex = Mutex()

		override suspend fun <T> released(block: suspend () -> T): T = handOver { terminal.released(block) }

		override val canSuspendProcess get() = terminal.canSuspendProcess

		override suspend fun suspendProcess(): Boolean {
			// Checked first, so that nothing is handed over in vain.
			if (!terminal.canSuspendProcess) return false
			return handOver { terminal.suspendProcess() }
		}

		/** Stops rendering and intercepting output while [block] hands the terminal to others. */
		private suspend fun <T> handOver(block: suspend () -> T): T = mutex.withLock {
			withContext(frameContext) {
				handedOver = true
				onRelease()
			}
			try {
				block()
			} finally {
				withContext(frameContext + NonCancellable) {
					onReacquire()
					handedOver = false
					needLayout = true
				}
			}
		}
	}

	override val lifecycle = LifecycleRegistry.createUnsafe(this)

	private var terminalState = mutableStateOf(
		TerminalState(
			terminal.state.focused.value,
			terminal.state.theme.value,
			terminal.state.size.value,
		),
	).also { state ->
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.focused.collect { focused ->
				state.value = state.value.copy(focused = focused)

				lifecycle.handleLifecycleEvent(
					if (focused) Lifecycle.Event.ON_RESUME else Lifecycle.Event.ON_PAUSE,
				)
			}
		}
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.theme.collect { theme ->
				state.value = state.value.copy(theme = theme)
			}
		}
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.size.collect { size ->
				state.value = state.value.copy(size = size)
			}
		}
	}

	private val applyObserverHandle: ObserverHandle

	private val readStatesOnLayout = mutableScatterSetOf<Any>()
	private val readStatesOnLayoutObserver: (Any) -> Unit = readStatesOnLayout::add

	private val readStatesOnDraw = mutableScatterSetOf<Any>()
	private val readStatesOnDrawObserver: (Any) -> Unit = readStatesOnDraw::add

	@Volatile
	private var needLayout = false

	@Volatile
	private var needDraw = false

	/** While true, the terminal belongs to other programs, so nothing is drawn. */
	@Volatile
	private var handedOver = false

	init {
		GlobalSnapshotManager().ensureStarted(scope)
		startRecomposer()
		startFrameListener()
		applyObserverHandle = registerSnapshotApplyObserver()
	}

	private fun performLayout() {
		needLayout = false

		readStatesOnLayout.clear()
		Snapshot.observe(readObserver = readStatesOnLayoutObserver) {
			val constraints = rootConstraints(terminalState.value.size)
			rootNode.measureAndPlace(constraints)
			popupHost.layout(rootNode, constraints)
		}
		if (focusOwner.onLayout()) {
			// Send the focus changes now, which this frame draws, rather than scheduling another frame.
			Snapshot.sendApplyNotifications()
		}

		performDraw()
	}

	private fun performDraw() {
		needDraw = false
		onDraw(this)
	}

	override fun draw(): TextCanvas {
		readStatesOnDraw.clear()
		val canvas = Snapshot.observe(readObserver = readStatesOnDrawObserver) {
			popupHost.draw(rootNode, rootConstraints(terminalState.value.size))
		}
		val surface = canvas as? TextSurface ?: return canvas
		lastSurface = surface
		selection?.range(surface)?.let { (start, end) -> surface.invert(start, end) }
		return canvas
	}

	/** The surface drawn last, which text is selected from. */
	private var lastSurface: TextSurface? = null

	/** Text selected with the mouse, where no pointer handler took the press. */
	private var selection: ScreenSelection? = null

	/** Whether the left button was pressed where no pointer handler took it, and still is. */
	private var selecting = false
	private var selectionAnchorX = 0
	private var selectionAnchorY = 0
	private var lastClick: TimeMark? = null
	private var lastClickX = 0
	private var lastClickY = 0
	private var clicks = 0

	private fun clearSelection() {
		if (selection != null) {
			selection = null
			needDraw = true
		}
	}

	/**
	 * Selects text when dragging where no pointer handler took the press, like the terminal itself
	 * would without mouse tracking, and copies it to the clipboard once released. Double clicks
	 * select words, and triple clicks rows.
	 */
	private fun updateSelection(type: PointerEventType, x: Int, y: Int, button: PointerButton, handled: Boolean) {
		when (type) {
			PointerEventType.Press -> {
				clearSelection()
				selecting = false
				if (handled || button != PointerButton.Left) return
				val repeated = lastClick.let { it != null && it.elapsedNow() < MultiClickInterval } &&
					x == lastClickX && y == lastClickY
				clicks = if (repeated) clicks % 3 + 1 else 1
				lastClick = TimeSource.Monotonic.markNow()
				lastClickX = x
				lastClickY = y
				selecting = true
				selectionAnchorX = x
				selectionAnchorY = y
				if (clicks > 1) {
					val unit = if (clicks == 2) ScreenSelection.Unit.Words else ScreenSelection.Unit.Rows
					selection = ScreenSelection(x, y, x, y, unit)
					needDraw = true
				}
			}

			PointerEventType.Drag -> if (selecting) {
				val unit = selection?.unit ?: ScreenSelection.Unit.Cells
				selection = ScreenSelection(selectionAnchorX, selectionAnchorY, x, y, unit)
				needDraw = true
			}

			PointerEventType.Release -> if (selecting) {
				selecting = false
				val surface = lastSurface ?: return
				val (start, end) = selection?.range(surface) ?: return
				val text = surface.textBetween(start, end)
				if (text.isNotEmpty()) clipboard.setText(text)
			}

			else -> {}
		}
	}

	override fun static(): String? {
		var static = staticLogs.tryReceive().getOrNull()
		if (static == null) {
			return null
		}
		return buildString {
			do {
				when (static) {
					is String -> {
						append(static)
						append("\r\n")
					}

					is TextCanvas -> {
						for (row in 0 until static.height) {
							static.appendRowTo(
								appendable = this,
								row = row,
								ansiLevel = terminal.capabilities.ansiLevel,
								supportsKittyUnderlines = terminal.capabilities.kittyUnderline,
							)
							append("\r\n")
						}
					}
				}

				static = staticLogs.tryReceive().getOrNull()
			} while (static != null)

			// Remove trailing "\r\n".
			setLength(length - 2)
		}
	}

	override fun title(): String? = windowTitles.current

	override fun pointerShape(): String? = pointerShapes.current?.name

	override fun terminalCommands(): String? {
		var command = commands.tryReceive().getOrNull() ?: return null
		return buildString {
			do {
				append(command)
				command = commands.tryReceive().getOrNull() ?: break
			} while (true)
		}
	}

	/** Shows [text] above the display. Safe to call from any thread. */
	fun logStatic(text: String) {
		staticLogs.trySend(text)
		needDraw = true
	}

	override fun dumpNodes(): String {
		return buildString {
			append(rootNode)
			for (popup in popupHost.popups) {
				append("\n")
				append(popup.root)
			}
		}
	}

	private fun registerSnapshotApplyObserver(): ObserverHandle {
		return Snapshot.registerApplyObserver { changedStates, _ ->
			if (!needLayout) {
				var setDraw = needDraw
				for (state in changedStates) {
					if (state in readStatesOnLayout) {
						needLayout = true
						break
					}
					if (setDraw) {
						continue
					}
					if (state in readStatesOnDraw) {
						setDraw = true
						needDraw = true
					}
				}
			}
		}
	}

	private fun startRecomposer() {
		scope.launch(start = UNDISPATCHED) {
			recomposer.runRecomposeAndApplyChanges()
		}
	}

	private fun startFrameListener() {
		scope.launch(start = UNDISPATCHED) {
			val ctrlC = KeyEvent("c", ctrl = true)
			val ctrlZ = KeyEvent("z", ctrl = true)
			// Key events received since the start of a bracketed paste, or null when not pasting.
			var pasted: MutableList<KeyboardEvent>? = null

			/** Returns false when the key was an unhandled Ctrl+C which cancelled the program. */
			fun sendKeyEvent(event: KeyboardEvent): Boolean {
				val keyEvent = event.toKeyEventOrNull() ?: return true
				clearSelection()
				val keyHandled = focusOwner.dispatch { root, phase -> root.sendKeyEvent(keyEvent, phase) } ||
					focusOwner.onUnhandledKeyEvent(keyEvent)
				if (!keyHandled && keyEvent == ctrlC) {
					job.cancel()
					return false
				}
				if (!keyHandled && keyEvent == ctrlZ && terminal.canSuspendProcess) {
					// Like in a shell, which sends SIGTSTP for it unless the terminal is in raw mode.
					scope.launch { terminalControl.suspendProcess() }
				}
				return true
			}

			do {
				externalClock.withFrameNanos { nanos ->
					// Drain any pending input events before triggering the frame.
					while (true) {
						when (val event = terminal.events.tryReceive().getOrNull() ?: break) {
							is BracketedPasteEvent -> if (event.start) {
								pasted = mutableListOf()
							} else {
								val events = pasted ?: continue
								pasted = null
								val pasteEvent = PasteEvent(events.toPastedText())
								if (!focusOwner.dispatch { root, phase -> root.sendPasteEvent(pasteEvent, phase) }) {
									// Nothing handled the paste, so fall back to delivering it as key presses.
									for (keyboardEvent in events) {
										if (!sendKeyEvent(keyboardEvent)) return@withFrameNanos
									}
								}
							}

							is KeyboardEvent -> {
								val events = pasted
								if (events != null) {
									events += event
								} else if (!sendKeyEvent(event)) {
									return@withFrameNanos
								}
							}

							is MouseEvent -> sendMouseEvent(event)

							is CursorPositionEvent -> pointerMapping.onCursorPosition(event.row, event.column)

							else -> {}
						}
					}

					internalClock.sendFrame(nanos)

					if (handedOver) {
						// Drawn once the terminal is taken over again.
					} else if (needLayout) {
						performLayout()
					} else if (needDraw) {
						performDraw()
					}
				}
			} while (job.isActive)
		}
	}

	private fun sendMouseEvent(event: MouseEvent) {
		val row = pointerMapping.surfaceRow(event.y) ?: return
		val button = when (event.button) {
			MouseEvent.Button.Left -> PointerButton.Left
			MouseEvent.Button.Middle -> PointerButton.Middle
			MouseEvent.Button.Right -> PointerButton.Right
			else -> PointerButton.None
		}
		val wheel = when (event.button) {
			MouseEvent.Button.WheelUp -> -1
			MouseEvent.Button.WheelDown -> 1
			else -> 0
		}
		val type = when {
			wheel != 0 -> if (event.type == MouseEvent.Type.Press) PointerEventType.Scroll else return

			else -> when (event.type) {
				MouseEvent.Type.Press -> PointerEventType.Press
				MouseEvent.Type.Release -> PointerEventType.Release
				MouseEvent.Type.Drag -> PointerEventType.Drag
				MouseEvent.Type.Motion -> PointerEventType.Move
			}
		}
		val handled = pointerDispatcher.dispatch(type, event.x, row, button, wheel, event.shift, event.alt, event.ctrl)
		updateSelection(type, event.x, row, button, handled)
	}

	override fun setContent(content: @Composable () -> Unit) {
		composition.setContent {
			CompositionLocalProvider(
				LocalTerminalState provides terminalState.value,
				LocalStaticLogger provides staticLogger,
				LocalWindowTitles provides windowTitles,
				LocalPointerShapes provides pointerShapes,
				LocalClipboard provides clipboard,
				LocalNotifier provides notifier,
				LocalLifecycleOwner provides this,
				LocalLayoutInvalidator provides layoutInvalidator,
				LocalFocusManager provides focusOwner,
				LocalPopupHost provides popupHost,
				LocalTerminalCapabilities provides terminal.capabilities,
				LocalTerminalName provides terminal.name,
				LocalTerminalCommands provides terminalCommandSender,
				LocalTerminalControl provides terminalControl,
				content = content,
			)
		}
		performLayout()
	}

	override suspend fun awaitComplete() {
		try {
			val effectJob = checkNotNull(recomposer.effectCoroutineContext[Job]) {
				"No Job in effectCoroutineContext of recomposer"
			}
			effectJob.children.forEach { it.join() }
			recomposer.awaitIdle()

			applyObserverHandle.dispose()
			if (needLayout || needDraw) {
				// TODO Re-evaluate this for correctness.
				scope.launch { internalClock.withFrameNanos { } }.join()
			}

			recomposer.close()
			recomposer.join()
		} finally {
			applyObserverHandle.dispose() // if canceled before dispose in the try block
			job.cancel()
		}
	}

	override fun cancel() {
		applyObserverHandle.dispose()
		recomposer.cancel()
		job.cancel()
	}
}

/** How soon another click at the same position counts towards a double or triple click. */
private val MultiClickInterval = 500.milliseconds

/** Text selected with the mouse from where it was pressed to where it is now, in surface coordinates. */
private class ScreenSelection(
	val anchorX: Int,
	val anchorY: Int,
	val x: Int,
	val y: Int,
	val unit: Unit,
) {
	enum class Unit { Cells, Words, Rows }

	/** The selected pixels in reading order, as the start and exclusive end, or null when none. */
	fun range(surface: TextSurface): Pair<Int, Int>? {
		if (surface.width == 0 || surface.height == 0) return null
		fun index(x: Int, y: Int) = y.coerceIn(0, surface.height - 1) * surface.width + x.coerceIn(0, surface.width - 1)
		val anchor = index(anchorX, anchorY)
		val current = index(x, y)
		val first = minOf(anchor, current)
		val last = maxOf(anchor, current)
		return when (unit) {
			Unit.Cells -> surface.characterRange(first, last)
			Unit.Words -> surface.wordAt(first).first to surface.wordAt(last).second
			Unit.Rows -> first / surface.width * surface.width to (last / surface.width + 1) * surface.width
		}
	}
}

/** Requests another layout, for changes the snapshot system does not track, like subcompositions. */
internal val LocalLayoutInvalidator = staticCompositionLocalOf<() -> Unit> {
	throw IllegalStateException("No layout invalidator provided")
}

internal class MosaicNodeApplier(
	private val onChanges: () -> Unit = {},
) : AbstractApplier<MosaicNode>(
	root = MosaicNode(
		measurePolicy = BoxMeasurePolicy(),
		debugPolicy = { children.joinToString(separator = "\n") },
		isStatic = false,
	),
) {
	override fun onBeginChanges() {
		super.onBeginChanges()
		// We invoke this here rather than in the end change callback to try and ensure
		// no one relies on it to signal the end of changes.
		onChanges.invoke()
	}

	override fun insertTopDown(index: Int, instance: MosaicNode) {
		// Ignored, we insert bottom-up.
	}

	override fun insertBottomUp(index: Int, instance: MosaicNode) {
		current.children.add(index, instance)
	}

	override fun remove(index: Int, count: Int) {
		current.children.remove(index, count)
	}

	override fun move(from: Int, to: Int, count: Int) {
		current.children.move(from, to, count)
	}

	override fun onClear() {
	}
}

@OptIn(ExperimentalAtomicApi::class)
internal class GlobalSnapshotManager {
	private val started = AtomicBoolean(false)
	private val sent = AtomicBoolean(false)

	fun ensureStarted(scope: CoroutineScope) {
		if (started.compareAndSet(expectedValue = false, newValue = true)) {
			val channel = Channel<Unit>(1)
			scope.launch {
				channel.consumeEach {
					sent.store(false)
					Snapshot.sendApplyNotifications()
				}
			}
			Snapshot.registerGlobalWriteObserver {
				if (sent.compareAndSet(expectedValue = false, newValue = true)) {
					channel.trySend(Unit)
				}
			}
		}
	}
}
