package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.modifier.composed

/**
 * Lets this node receive focus.
 *
 * Key and paste events go to the focused node and its ancestors first: [onPreviewKeyEvent] from
 * the root down to it, then [onKeyEvent] from it up to the root. Only when none of them handle an
 * event is it offered to the rest of the nodes.
 *
 * Tab and Shift+Tab move the focus to the next and previous focusable node in the order they are
 * composed, unless a node handles them. When nothing is focused, the first focusable node gets the
 * focus, unless it was cleared with [FocusManager.clearFocus].
 */
@Suppress("ktlint:compose:modifier-composed-check") // Mosaic has no Modifier.Node to keep state.
public fun Modifier.focusable(enabled: Boolean = true): Modifier = composed {
	val target = rememberFocusTarget()
	SideEffect {
		target.enabled = enabled
	}
	focusTarget(target)
}

/** Lets [focusRequester] move the focus to this node, or the first focusable node inside it. */
public fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier = this then FocusRequesterElement(focusRequester)

/**
 * Calls [onFocusChanged] with the focus state of this node when it is first laid out, and whenever
 * it changes after that.
 */
@Suppress("ktlint:compose:modifier-composed-check") // Mosaic has no Modifier.Node to keep state.
public fun Modifier.onFocusChanged(onFocusChanged: (FocusState) -> Unit): Modifier = composed {
	val observer = remember { FocusObserver() }
	observer.onFocusChanged = onFocusChanged
	FocusObserverElement(observer)
}

/** Whether a node is focused, or contains the focused node. */
@Poko
public class FocusState(
	/** Whether this node is focused. */
	public val isFocused: Boolean,
	/** Whether this node or one of the nodes inside it is focused. */
	public val hasFocus: Boolean,
)

/** Moves the focus to the nodes it is attached to with [Modifier.focusRequester]. */
@Stable
public class FocusRequester {
	internal val nodes = LinkedHashSet<MosaicNode>()
	private var pending = false

	/**
	 * Focuses the node this is attached to, or the first focusable node inside it. When it is not
	 * laid out yet, it is focused once it is.
	 */
	public fun requestFocus() {
		for (node in nodes) {
			val target = node.firstFocusTarget() ?: continue
			if (target.owner.focus(target)) {
				pending = false
				return
			}
		}
		pending = true
	}

	internal fun attach(node: MosaicNode) {
		nodes += node
		if (pending) requestFocus()
	}
}

/** Moves the focus between focusable nodes. */
public interface FocusManager {
	/** Moves the focus in [direction], wrapping around. Returns false without focusable nodes. */
	public fun moveFocus(direction: FocusDirection): Boolean

	/** Removes the focus, until it is requested again or moved with [moveFocus]. */
	public fun clearFocus()
}

public enum class FocusDirection {
	Next,
	Previous,
}

public val LocalFocusManager: ProvidableCompositionLocal<FocusManager> = staticCompositionLocalOf {
	throw AssertionError("No FocusManager provided")
}

internal fun Modifier.focusTarget(target: FocusTarget): Modifier = this then FocusTargetElement(target)

@Composable
internal fun rememberFocusTarget(): FocusTarget {
	val owner = LocalFocusManager.current as FocusOwner
	val target = remember(owner) { FocusTarget(owner) }
	DisposableEffect(target) {
		onDispose {
			target.detach()
		}
	}
	return target
}

internal class FocusTarget(val owner: FocusOwner) {
	var isFocused by mutableStateOf(false)
		internal set

	var enabled = true
		set(value) {
			field = value
			if (!value) owner.onCannotFocus(this)
		}

	private var attached = true

	val canFocus get() = enabled && attached

	fun detach() {
		attached = false
		owner.onCannotFocus(this)
	}
}

internal class FocusObserver {
	var onFocusChanged: (FocusState) -> Unit = {}
	private var state: FocusState? = null

	fun update(state: FocusState) {
		if (this.state != state) {
			this.state = state
			onFocusChanged(state)
		}
	}
}

internal class FocusTargetElement(val target: FocusTarget) : Modifier.Element {
	override fun toString() = "FocusTarget"
}

internal class FocusRequesterElement(val requester: FocusRequester) : Modifier.Element {
	override fun toString() = "FocusRequester"
}

internal class FocusObserverElement(val observer: FocusObserver) : Modifier.Element {
	override fun toString() = "OnFocusChanged"
}

internal fun MosaicNode.firstFocusTarget(): FocusTarget? {
	focusTargets.firstOrNull { it.canFocus }?.let { return it }
	for (child in children) {
		child.firstFocusTarget()?.let { return it }
	}
	return null
}

/** Which nodes an event is offered to, see [Modifier.focusable]. */
internal enum class DispatchPhase {
	/** Nothing is focused, so every node gets the event. */
	All,

	/** Only the focused node and its ancestors get the event. */
	FocusedPath,

	/** The nodes which did not get the event during [FocusedPath]. */
	Rest,
}

/**
 * Moves the focus between the nodes of [roots], which are the root of the composition followed by
 * the roots of its popups in the order they are drawn.
 */
internal class FocusOwner(private val roots: () -> List<MosaicNode>) : FocusManager {
	var focused: FocusTarget? = null
		private set

	/** Set once the focus was cleared on purpose, so that it is not moved to the first node. */
	private var cleared = false

	private val path = ArrayList<MosaicNode>()

	fun focus(target: FocusTarget): Boolean {
		if (!target.canFocus) return false
		cleared = false
		if (focused !== target) {
			focused?.isFocused = false
			focused = target
			target.isFocused = true
			notifyObservers()
		}
		return true
	}

	override fun moveFocus(direction: FocusDirection): Boolean {
		val targets = ArrayList<FocusTarget>()
		for (root in roots()) {
			root.collectFocusTargets(targets)
		}
		if (targets.isEmpty()) return false
		val index = targets.indexOf(focused)
		val next = when (direction) {
			FocusDirection.Next -> if (index == -1) 0 else (index + 1) % targets.size
			FocusDirection.Previous -> if (index == -1) targets.lastIndex else (index - 1 + targets.size) % targets.size
		}
		return focus(targets[next])
	}

	override fun clearFocus() {
		cleared = true
		val focused = focused ?: return
		focused.isFocused = false
		this.focused = null
		notifyObservers()
	}

	fun onCannotFocus(target: FocusTarget) {
		if (focused === target) {
			target.isFocused = false
			focused = null
			// Observers are notified after the next layout, which also moves the focus.
		}
	}

	/** Moves the focus to the first node when there is none, and tells observers about changes. */
	fun onLayout() {
		val focused = focused
		if (focused != null && roots().none { it.pathTo(focused, ArrayList()) != null }) {
			// The node of the target was removed while the target itself is still composed.
			onCannotFocus(focused)
		}
		if (this.focused == null && !cleared) {
			val target = roots().firstNotNullOfOrNull { it.firstFocusTarget() }
			if (target != null) {
				focus(target)
				return
			}
		}
		notifyObservers()
	}

	/**
	 * Offers an event to the focused path first, then to the rest of the nodes, starting with the
	 * popup drawn last.
	 */
	fun dispatch(send: (root: MosaicNode, phase: DispatchPhase) -> Boolean): Boolean {
		updatePath()
		val roots = roots()
		val focusedRoot = path.firstOrNull()
		if (focusedRoot != null && send(focusedRoot, DispatchPhase.FocusedPath)) return true
		for (index in roots.indices.reversed()) {
			val root = roots[index]
			if (send(root, if (root === focusedRoot) DispatchPhase.Rest else DispatchPhase.All)) return true
		}
		return false
	}

	fun onUnhandledKeyEvent(event: KeyEvent): Boolean {
		if (event.key != "Tab" || event.ctrl || event.alt) return false
		return moveFocus(if (event.shift) FocusDirection.Previous else FocusDirection.Next)
	}

	fun updatePath() {
		for (node in path) {
			node.containsFocus = false
		}
		path.clear()
		val focused = focused ?: return
		for (root in roots()) {
			if (root.pathTo(focused, path) != null) break
		}
		for (node in path) {
			node.containsFocus = true
		}
	}

	private fun notifyObservers() {
		for (root in roots()) {
			root.notifyFocusObservers(focused)
		}
	}
}

private fun MosaicNode.collectFocusTargets(targets: MutableList<FocusTarget>) {
	focusTargets.filterTo(targets) { it.canFocus }
	for (child in children) {
		child.collectFocusTargets(targets)
	}
}

/** Adds the nodes from this one to the one holding [target] to [path], or returns null. */
private fun MosaicNode.pathTo(target: FocusTarget, path: MutableList<MosaicNode>): List<MosaicNode>? {
	path += this
	if (target in focusTargets) return path
	for (child in children) {
		if (child.pathTo(target, path) != null) return path
	}
	path.removeAt(path.lastIndex)
	return null
}

/** Returns whether this node contains [focused]. */
private fun MosaicNode.notifyFocusObservers(focused: FocusTarget?): Boolean {
	val isFocused = focused != null && focused in focusTargets
	var hasFocus = isFocused
	for (child in children) {
		if (child.notifyFocusObservers(focused)) hasFocus = true
	}
	if (focusObservers.isNotEmpty()) {
		val state = FocusState(isFocused, hasFocus)
		for (observer in focusObservers) {
			observer.update(state)
		}
	}
	return hasFocus
}
