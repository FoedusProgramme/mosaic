package example

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import org.unitedsoftware.mosaic.layout.DrawStyle
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.offset
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.runMosaicMain
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.unit.IntOffset

private const val worldWidth = 20
private const val worldHeight = 10

private const val worldBorderChar = '*'
private const val worldBorderWidth = 1

private const val robotWidth = 3
private const val robotHeight = 1

fun main() = runMosaicMain {
	var x by remember { mutableIntStateOf(0) }
	var y by remember { mutableIntStateOf(0) }
	var exit by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier.onKeyEvent {
			when (it) {
				KeyEvent("ArrowUp") -> y = (y - 1).coerceAtLeast(0)
				KeyEvent("ArrowDown") -> y = (y + 1).coerceAtMost(worldHeight - robotHeight)
				KeyEvent("ArrowLeft") -> x = (x - 1).coerceAtLeast(0)
				KeyEvent("ArrowRight") -> x = (x + 1).coerceAtMost(worldWidth - robotWidth)
				KeyEvent("q") -> exit = true
				else -> return@onKeyEvent false
			}
			true
		},
	) {
		Text("Use arrow keys to move the face. Press “q” to exit.")
		Text("Position: $x, $y   Robot: $robotWidth, $robotHeight   World: $worldWidth, $worldHeight")
		Spacer(Modifier.height(1))
		Box(
			modifier = Modifier
				.drawBehind { drawRect(worldBorderChar, drawStyle = DrawStyle.Stroke(worldBorderWidth)) }
				.padding(worldBorderWidth)
				.size(worldWidth, worldHeight),
		) {
			Text("^_^", modifier = Modifier.offset { IntOffset(x, y) })
		}
	}

	if (!exit) {
		LaunchedEffect(Unit) {
			awaitCancellation()
		}
	}
}
