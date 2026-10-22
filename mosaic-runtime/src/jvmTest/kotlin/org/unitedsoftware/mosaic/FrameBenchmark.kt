package org.unitedsoftware.mosaic

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle

/**
 * Measures whole frames: changing state, recomposition, layout, drawing, and rendering to ANSI.
 * Only runs with MOSAIC_BENCHMARK set to the file to write results to, as tab separated values.
 */
class FrameBenchmark {
	private class Scenario(
		val name: String,
		/** Sends input before each frame, instead of changing the tick. */
		val keys: Boolean = false,
		val content: @Composable (tick: MutableIntState) -> Unit,
	)

	private val line = "The quick brown fox jumps over the lazy dog, again and again and again."
	private val paragraph = (1..5).joinToString(" ") { line }

	private val scenarios = listOf(
		Scenario("chat-200-last-line") { tick ->
			Column {
				for (index in 0 until 199) Text("$index $line")
				Text("streaming ${tick.intValue} $line")
			}
		},
		Scenario("styled-200-last-line") { tick ->
			Column {
				for (index in 0 until 200) {
					val changing = index == 199
					Text(
						buildAnnotatedString {
							append("$index ")
							withStyle(SpanStyle(color = Color.Red)) { append("quick ") }
							withStyle(SpanStyle(textStyle = TextStyle.Bold)) { append("brown ") }
							withStyle(SpanStyle(background = Color.Blue)) { append("fox ") }
							withStyle(SpanStyle(textStyle = TextStyle.Italic, color = Color.Green)) { append("jumps ") }
							append(if (changing) "over ${tick.intValue}" else "over the lazy dog")
						},
					)
				}
			}
		},
		Scenario("all-60-rows-change") { tick ->
			Column {
				for (index in 0 until 60) Text("$index frame ${tick.intValue} $line")
			}
		},
		Scenario("layout-40x8-boxes") { tick ->
			Column {
				for (row in 0 until 40) {
					Row {
						for (column in 0 until 8) {
							Box(modifier = Modifier.padding(left = 1).background(Color.Blue)) {
								Text(if (row == 0 && column == 0) "${tick.intValue % 100}" else "$row:$column")
							}
						}
					}
				}
			}
		},
		Scenario("draw-only-120x40") { tick ->
			Box(
				modifier = Modifier.size(120, 40).drawBehind {
					val value = tick.intValue
					for (row in 0 until 40) {
						drawText(row, 0, "row $row value $value $line", foreground = Color.Green)
					}
				},
			)
		},
		Scenario("wrapped-100-paragraphs") { tick ->
			Box(modifier = Modifier.width(80)) {
				Column {
					for (index in 0 until 99) Text("$index $paragraph")
					Text("${tick.intValue} $paragraph")
				}
			}
		},
		Scenario("keys-300-handlers", keys = true) { tick ->
			Column(
				modifier = Modifier.onKeyEvent {
					tick.intValue++
					true
				},
			) {
				Text("keys ${tick.intValue}")
				for (index in 0 until 300) {
					Text("$index", modifier = Modifier.onKeyEvent { false })
				}
			}
		},
	)

	@Test fun run() {
		val output = System.getenv("MOSAIC_BENCHMARK") ?: return
		val warmup = System.getenv("MOSAIC_BENCHMARK_WARMUP")?.toInt() ?: 300
		val frames = System.getenv("MOSAIC_BENCHMARK_FRAMES")?.toInt() ?: 1000
		val only = System.getenv("MOSAIC_BENCHMARK_ONLY")
		val results = scenarios.filter { only == null || it.name == only }.map { measure(it, warmup, frames) }
		File(output).appendText(results.joinToString("\n", postfix = "\n"))
	}

	private fun measure(scenario: Scenario, warmup: Int, count: Int): String = runBlocking {
		val clock = BroadcastFrameClock()
		val terminal = TestTerminal()
		val rendering = AnsiRendering(terminal.capabilities)
		var drawn = 0
		var bytes = 0L
		var renderNanos = 0L
		val tick = mutableIntStateOf(0)
		val mosaic = Mosaic(
			coroutineContext = coroutineContext + clock,
			onDraw = {
				val start = System.nanoTime()
				bytes += rendering.render(it).length
				renderNanos += System.nanoTime() - start
				drawn++
			},
			terminal = terminal,
		)
		mosaic.setContent { scenario.content(tick) }

		suspend fun frame() {
			val before = drawn
			if (scenario.keys) {
				terminal.events.trySend(KeyboardEvent('x'.code))
			} else {
				tick.intValue++
				Snapshot.sendApplyNotifications()
			}
			var attempts = 0
			while (drawn == before) {
				yield()
				clock.sendFrame(System.nanoTime())
				check(++attempts < 1000) { "No frame for ${scenario.name}" }
			}
		}

		repeat(warmup) { frame() }
		val windowStart = System.currentTimeMillis()
		bytes = 0
		renderNanos = 0
		val times = LongArray(count)
		for (index in 0 until count) {
			val start = System.nanoTime()
			frame()
			times[index] = System.nanoTime() - start
		}
		mosaic.cancel()
		System.getenv("MOSAIC_BENCHMARK_WINDOWS")?.let {
			File(it).appendText("${scenario.name}\t$windowStart\t${System.currentTimeMillis()}\n")
		}

		times.sort()
		fun micros(nanos: Long) = "%.1f".format(nanos / 1000.0)
		listOf(
			scenario.name,
			micros(times[count / 2]),
			micros(times[count * 9 / 10]),
			micros(times.sum() / count),
			micros(renderNanos / count),
			(bytes / count).toString(),
		).joinToString("\t")
	}
}
