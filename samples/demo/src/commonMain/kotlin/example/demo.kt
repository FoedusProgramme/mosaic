package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import org.unitedsoftware.mosaic.LocalTerminalState
import org.unitedsoftware.mosaic.animation.Animatable
import org.unitedsoftware.mosaic.animation.LinearEasing
import org.unitedsoftware.mosaic.animation.Spring
import org.unitedsoftware.mosaic.animation.VectorConverter
import org.unitedsoftware.mosaic.animation.animateValue
import org.unitedsoftware.mosaic.animation.infiniteRepeatable
import org.unitedsoftware.mosaic.animation.rememberInfiniteTransition
import org.unitedsoftware.mosaic.animation.spring
import org.unitedsoftware.mosaic.animation.tween
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.runMosaicMain
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.ColumnScope
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.UnderlineStyle

private val BrightGreen = Color(100, 255, 100)
private val BrightBlue = Color(60, 140, 230)

fun main() = runMosaicMain {
	Column {
		TerminalInfo()
		Spacer(modifier = Modifier.height(1))
		GradientsBlock()
	}
	LaunchedEffect(Unit) {
		awaitCancellation()
	}
}

@Composable
private fun TerminalInfo() {
	val widthAndHeightTitleColorAnimatable = remember { Animatable(Color.White) }

	val screenSize = LocalTerminalState.current.size
	val screenWidth = screenSize.columns
	val screenHeight = screenSize.rows

	val screenWidthState = rememberUpdatedIntState(screenWidth)
	val screenHeightState = rememberUpdatedIntState(screenHeight)

	val widthValueColor by terminalSizeValueColorAsState(screenWidthState)
	val heightValueColor by terminalSizeValueColorAsState(screenHeightState)

	Text(
		buildAnnotatedString {
			append("\uD83D\uDDA5\uFE0F")
			append(" ")
			append("Terminal(")
			withStyle(SpanStyle(color = widthAndHeightTitleColorAnimatable.value)) {
				append("width=")
			}
			withStyle(
				SpanStyle(
					color = widthValueColor,
					textStyle = TextStyle.Bold,
					underlineStyle = UnderlineStyle.Straight,
				),
			) {
				append(screenWidth.toString())
			}
			append(", ")
			withStyle(SpanStyle(color = widthAndHeightTitleColorAnimatable.value)) {
				append("height=")
			}
			withStyle(
				SpanStyle(
					color = heightValueColor,
					textStyle = TextStyle.Bold,
					underlineStyle = UnderlineStyle.Straight,
				),
			) {
				append(screenHeight.toString())
			}
			append(")")
			append(" ")
			append("\uD83D\uDDA5\uFE0F")
		},
	)
	LaunchedEffect(Unit) {
		widthAndHeightTitleColorAnimatable.animateTo(
			targetValue = BrightGreen,
			animationSpec = tween(durationMillis = 2000, delayMillis = 500),
		)
	}
}

@Composable
@Suppress("ktlint:compose:state-param-check")
private fun terminalSizeValueColorAsState(valueState: IntState): State<Color> {
	var previousValue by remember { mutableIntStateOf(valueState.intValue) }
	val valueColorAnimatable = remember { Animatable(BrightBlue) }
	LaunchedEffect(Unit) {
		snapshotFlow { valueState.intValue }
			.collectLatest { value ->
				val diff = abs(value - previousValue)
				if (diff == 0) {
					return@collectLatest
				}
				val normalizedDiff = (diff / 20f).coerceIn(0f, 1f)
				valueColorAnimatable.animateTo(
					targetValue = Color((244 * normalizedDiff).toInt(), 151, 151), // BrightRed
					animationSpec = spring(
						dampingRatio = Spring.DampingRatioMediumBouncy,
						stiffness = Spring.StiffnessMediumLow,
					),
				)
				previousValue = value
				valueColorAnimatable.animateTo(
					targetValue = BrightBlue,
					animationSpec = tween(500),
				)
			}
	}
	return valueColorAnimatable.asState()
}

@Suppress("UnusedReceiverParameter") // instead of ignore rule: compose:multiple-emitters-check
@Composable
private fun ColumnScope.GradientsBlock() {
	val gradientWidthAnimatable = remember {
		Animatable(
			initialValue = 0,
			typeConverter = Int.VectorConverter,
			label = "Width",
		)
	}
	val gradientWidthState = gradientWidthAnimatable.asState()
	Gradient(
		repeatedWord = "Red",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(1.0f - percent, 0.0f, 0.0f) },
		backgroundColorProvider = { percent -> Color(percent, 0.0f, 0.0f) },
	)
	Gradient(
		repeatedWord = "Yellow",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(1.0f - percent, 1.0f - percent, 0.0f) },
		backgroundColorProvider = { percent -> Color(percent, percent, 0.0f) },
	)
	Gradient(
		repeatedWord = "Green",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(0.0f, 1.0f - percent, 0.0f) },
		backgroundColorProvider = { percent -> Color(0.0f, percent, 0.0f) },
	)
	Gradient(
		repeatedWord = "Cyan",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(0.0f, 1.0f - percent, 1.0f - percent) },
		backgroundColorProvider = { percent -> Color(0.0f, percent, percent) },
	)
	Gradient(
		repeatedWord = "Blue",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(0.0f, 0.0f, 1.0f - percent) },
		backgroundColorProvider = { percent -> Color(0.0f, 0.0f, percent) },
	)
	Gradient(
		repeatedWord = "Magenta",
		widthState = gradientWidthState,
		textColorProvider = { percent -> Color(1.0f - percent, 0.0f, 1.0f - percent) },
		backgroundColorProvider = { percent -> Color(percent, 0.0f, percent) },
	)
	val screenHalfWidth = LocalTerminalState.current.size.columns / 2
	LaunchedEffect(screenHalfWidth) {
		gradientWidthAnimatable.animateTo(screenHalfWidth)
	}
}

@Composable
@Suppress("ktlint:compose:state-param-check")
private fun Gradient(
	repeatedWord: String,
	widthState: State<Int>,
	textColorProvider: (percent: Float) -> Color,
	backgroundColorProvider: (percent: Float) -> Color,
) {
	val textBias by rememberInfiniteTransition().animateValue(
		initialValue = repeatedWord.length,
		targetValue = 0,
		typeConverter = Int.VectorConverter,
		animationSpec = infiniteRepeatable(
			tween(repeatedWord.length * 200, easing = LinearEasing),
		),
	)
	Box {
		val width = widthState.value
		Row {
			var wordCharIndex = textBias
			repeat(width) { index ->
				if (wordCharIndex == repeatedWord.length) {
					wordCharIndex = 0
				}
				Filler(
					char = repeatedWord[wordCharIndex],
					foreground = textColorProvider.invoke(index / width.toFloat()),
					modifier = Modifier.size(1),
				)
				wordCharIndex++
			}
		}
		Row {
			repeat(width) { index ->
				Spacer(
					modifier = Modifier
						.size(1)
						.background(backgroundColorProvider.invoke(index / width.toFloat())),
				)
			}
		}
	}
}

@Composable
fun rememberUpdatedIntState(newValue: Int): IntState = remember {
	mutableIntStateOf(newValue)
}.apply { value = newValue }
