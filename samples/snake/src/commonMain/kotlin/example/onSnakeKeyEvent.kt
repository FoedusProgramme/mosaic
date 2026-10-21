package example

import example.models.Direction
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.modifier.Modifier

private val Escape = KeyEvent("Escape")
private val q = KeyEvent("q")
private val Q = KeyEvent("Q")

private val r = KeyEvent("r")
private val R = KeyEvent("R")

private val Space = KeyEvent(" ")

private val ArrowLeft = KeyEvent("ArrowLeft")
private val a = KeyEvent("a")
private val A = KeyEvent("A")

private val ArrowRight = KeyEvent("ArrowRight")
private val d = KeyEvent("d")
private val D = KeyEvent("D")

private val ArrowUp = KeyEvent("ArrowUp")
private val w = KeyEvent("w")
private val W = KeyEvent("W")

private val ArrowDown = KeyEvent("ArrowDown")
private val s = KeyEvent("s")
private val S = KeyEvent("S")

internal fun Modifier.onSnakeKeyEvent(doAction: (SnakeUiAction) -> Unit): Modifier {
	return this.onKeyEvent {
		when (it) {
			Escape, q, Q -> doAction(SnakeUiAction.Exit)
			r, R -> doAction(SnakeUiAction.Restart)
			Space -> doAction(SnakeUiAction.SwitchPhase)
			ArrowLeft, a, A -> doAction(SnakeUiAction.ChangeDirection(Direction.LEFT))
			ArrowRight, d, D -> doAction(SnakeUiAction.ChangeDirection(Direction.RIGHT))
			ArrowUp, w, W -> doAction(SnakeUiAction.ChangeDirection(Direction.UP))
			ArrowDown, s, S -> doAction(SnakeUiAction.ChangeDirection(Direction.DOWN))
			else -> return@onKeyEvent false
		}
		true
	}
}
