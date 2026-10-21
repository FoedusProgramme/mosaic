package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.jakewharton.mosaic.LocalStaticLogger
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.StaticEffect
import com.jakewharton.mosaic.animation.EaseInOutBack
import com.jakewharton.mosaic.animation.EaseOutBounce
import com.jakewharton.mosaic.animation.EaseOutElastic
import com.jakewharton.mosaic.animation.FastOutSlowInEasing
import com.jakewharton.mosaic.animation.LinearEasing
import com.jakewharton.mosaic.animation.RepeatMode
import com.jakewharton.mosaic.animation.Spring
import com.jakewharton.mosaic.animation.animateColor
import com.jakewharton.mosaic.animation.animateFloat
import com.jakewharton.mosaic.animation.animateIntOffsetAsState
import com.jakewharton.mosaic.animation.infiniteRepeatable
import com.jakewharton.mosaic.animation.rememberInfiniteTransition
import com.jakewharton.mosaic.animation.spring
import com.jakewharton.mosaic.animation.tween
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.offset
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val easings = listOf(
	"LinearEasing" to LinearEasing,
	"FastOutSlowInEasing" to FastOutSlowInEasing,
	"EaseOutBounce" to EaseOutBounce,
	"EaseInOutBack" to EaseInOutBack,
	"EaseOutElastic" to EaseOutElastic,
)

@Composable
fun AnimationPage() {
	val palette = LocalPalette.current
	val transition = rememberInfiniteTransition("catalog")
	val pulse by transition.animateColor(
		initialValue = palette.accent,
		targetValue = palette.bad,
		animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
	)
	var right by remember { mutableStateOf(false) }
	// Travel stays inside the panel even with the spring's overshoot.
	val offset by animateIntOffsetAsState(
		targetValue = IntOffset(if (right) 30 else 5, 0),
		animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
	)
	Column(
		Modifier.onKeyEvent {
			if (it == KeyEvent(" ")) {
				right = !right
				true
			} else {
				false
			}
		},
	) {
		Text("InfiniteTransition.animateColor", textStyle = TextStyle.Bold)
		Text("●●● pulsing between two palette colors", color = pulse)
		Spacer(Modifier.height(1))
		Text("animateFloat with five easings", textStyle = TextStyle.Bold)
		for ((name, easing) in easings) {
			val progress by transition.animateFloat(
				initialValue = 0f,
				targetValue = 1f,
				animationSpec = infiniteRepeatable(tween(1600, easing = easing), RepeatMode.Reverse),
				label = name,
			)
			Row {
				Box(Modifier.width(20)) { Text(name, color = palette.muted) }
				ProgressBar(progress, width = 30)
			}
		}
		Spacer(Modifier.height(1))
		Text("animateIntOffsetAsState + spring: press space", textStyle = TextStyle.Bold)
		Panel(modifier = Modifier.width(40)) {
			Box(Modifier.fillMaxWidth()) {
				Text("◆", modifier = Modifier.offset { offset }, color = palette.accent)
			}
		}
	}
}

@Composable
fun TerminalStatePage() {
	val palette = LocalPalette.current
	val state = LocalTerminalState.current
	val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
	val scope = rememberCoroutineScope()
	var ticks by remember { mutableIntStateOf(0) }
	LifecycleResumeEffect(Unit) {
		val job = scope.launch {
			while (true) {
				delay(1000)
				ticks++
			}
		}
		onPauseOrDispose { job.cancel() }
	}
	Column {
		Labeled("size") {
			Text("${state.size.columns} × ${state.size.rows} cells", color = palette.accent, textStyle = TextStyle.Bold)
		}
		Labeled("pixel size") {
			val pixels = state.size
			Text(if (pixels.width == 0) "not reported" else "${pixels.width} × ${pixels.height} px")
		}
		Labeled("theme") { Text(state.theme.name) }
		Labeled("focused") {
			Text(state.focused.toString(), color = if (state.focused) palette.good else palette.bad)
		}
		Labeled("lifecycle") { Text(lifecycle.name) }
		Labeled("resumed for") { Text("${ticks}s") }
		Spacer(Modifier.height(1))
		Text("Resize the window, focus another window, or flip", color = palette.muted)
		Text("the terminal's color scheme to see these update.", color = palette.muted)
		Spacer(Modifier.height(1))
		Text("Focus drives LocalLifecycleOwner, so the counter", color = palette.muted)
		Text("above (a LifecycleResumeEffect) pauses while the", color = palette.muted)
		Text("terminal is unfocused. Values depend on terminal", color = palette.muted)
		Text("support: see Terminal.capabilities.", color = palette.muted)
	}
}

@Composable
fun StaticOutputPage() {
	val palette = LocalPalette.current
	val logger = LocalStaticLogger.current
	var count by remember { mutableIntStateOf(0) }
	StaticEffect {
		Text("Static output: the catalog opened the Static output page.", color = palette.accent)
	}
	Column(
		Modifier.onKeyEvent {
			if (it == KeyEvent("Enter")) {
				count++
				logger.log("Static log line #$count")
				true
			} else {
				false
			}
		},
	) {
		Text("Static output is printed above the live UI once")
		Text("and never redrawn. It suits logs and finished work,")
		Text("like the test results in the jest sample.")
		Spacer(Modifier.height(1))
		Labeled("StaticEffect") { Text("ran once when this page opened", color = palette.good) }
		Labeled("lines logged") { Text("$count", color = palette.accent, textStyle = TextStyle.Bold) }
		Spacer(Modifier.height(1))
		Text("This catalog fills the screen, so static lines go", color = palette.muted)
		Text("straight to scrollback: scroll up or quit to read.", color = palette.muted)
	}
}
