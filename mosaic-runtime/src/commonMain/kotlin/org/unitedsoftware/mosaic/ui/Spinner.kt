package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.drewhamilton.poko.Poko
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.columnWidth

/** The frames of a [Spinner], each shown for [interval] before the next. */
@Immutable
@Poko
public class SpinnerStyle(
	public val frames: List<String>,
	public val interval: Duration,
) {
	init {
		require(frames.isNotEmpty()) { "A spinner needs at least one frame" }
		require(interval.isPositive()) { "A spinner's interval must be positive: $interval" }
	}

	public companion object {
		/** Braille dots running around a cell, `⠋ ⠙ ⠹ …`. */
		public val Dots: SpinnerStyle = SpinnerStyle(
			listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"),
			80.milliseconds,
		)

		/** A turning line, `- \ | /`, which every terminal font has. */
		public val Line: SpinnerStyle = SpinnerStyle(listOf("-", "\\", "|", "/"), 120.milliseconds)

		/** A dot which grows and shrinks, `· • ● •`. */
		public val Pulse: SpinnerStyle = SpinnerStyle(listOf("·", "•", "●", "•"), 150.milliseconds)
	}
}

/**
 * An animation which shows the frames of [style] in turn for as long as it is composed, for work
 * whose progress is unknown. It is as wide as the widest frame, so that what follows it stays put.
 */
@Composable
public fun Spinner(
	modifier: Modifier = Modifier,
	style: SpinnerStyle = SpinnerStyle.Dots,
	color: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
) {
	var frame by remember(style) { mutableIntStateOf(0) }
	LaunchedEffect(style) {
		while (true) {
			delay(style.interval)
			frame = (frame + 1) % style.frames.size
		}
	}
	val width = remember(style) { style.frames.maxOf { it.columnWidth() } }
	Text(style.frames[frame], modifier.width(width), color = color, textStyle = textStyle)
}
