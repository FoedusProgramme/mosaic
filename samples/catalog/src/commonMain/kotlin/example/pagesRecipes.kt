package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.animation.FastOutSlowInEasing
import org.unitedsoftware.mosaic.animation.RepeatMode
import org.unitedsoftware.mosaic.animation.animateFloat
import org.unitedsoftware.mosaic.animation.infiniteRepeatable
import org.unitedsoftware.mosaic.animation.rememberInfiniteTransition
import org.unitedsoftware.mosaic.animation.tween
import org.unitedsoftware.mosaic.layout.BorderStyle
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.fillMaxWidth
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.LazyColumn
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.rememberLazyListState
import org.unitedsoftware.mosaic.ui.unit.IntOffset
import org.unitedsoftware.mosaic.ui.unit.IntSize

private val borderStyles = listOf(
	"Single" to BorderStyle.Single,
	"Rounded" to BorderStyle.Rounded,
	"Double" to BorderStyle.Double,
	"Heavy" to BorderStyle.Heavy,
	"Ascii" to BorderStyle.Ascii,
)

@Composable
fun PanelsPage() {
	val palette = LocalPalette.current
	Column {
		for (pair in borderStyles.chunked(2)) {
			Row {
				for ((name, style) in pair) {
					Panel(modifier = Modifier.size(24, 5), title = name, style = style) {
						Text("BorderStyle.$name", color = palette.muted)
					}
					Spacer(Modifier.width(2))
				}
				if (pair.size == 1) {
					Panel(
						modifier = Modifier.size(24, 5),
						title = "Colored",
						color = palette.accent,
						titleColor = palette.warn,
					) {
						Text("color + titleColor")
					}
				}
			}
		}
	}
}

@Composable
fun ProgressPage() {
	val palette = LocalPalette.current
	var progress by remember { mutableFloatStateOf(0f) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(40)
			progress = if (progress >= 1f) 0f else progress + 0.005f
		}
	}
	val bounce by rememberInfiniteTransition("indeterminate").animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
	)
	Column {
		Text("Determinate, with 1/8-cell precision", textStyle = TextStyle.Bold)
		Row {
			ProgressBar(progress, width = 40)
			Text("${(progress * 100).roundToInt()}%".padStart(5))
		}
		Row {
			ProgressBar(0.333f, width = 40, color = palette.good)
			Text("33%".padStart(5))
		}
		Row {
			ProgressBar(0.9f, width = 40, color = palette.warn)
			Text("90%".padStart(5))
		}
		Spacer(Modifier.height(1))
		Text("Indeterminate", textStyle = TextStyle.Bold)
		Box(
			modifier = Modifier.size(40, 1).background(palette.surface).drawBehind {
				val start = ((width - 8) * bounce).roundToInt()
				drawRect(' ', background = palette.accent, topLeft = IntOffset(start, 0), size = IntSize(8, 1))
			},
		)
		Spacer(Modifier.height(1))
		Text("Spinners", textStyle = TextStyle.Bold)
		for (style in SpinnerStyle.entries) {
			Row {
				Spinner(style)
				Text(" ${style.name}", color = palette.muted)
			}
		}
		Spacer(Modifier.height(1))
		Row {
			Spinner(SpinnerStyle.Dots, color = palette.good)
			Text(" Compiling catalog ")
			ProgressBar(progress, width = 20, color = palette.good)
		}
	}
}

@Composable
fun TabsPage() {
	val palette = LocalPalette.current
	val titles = listOf("Overview", "Processes", "Logs")
	var tab by remember { mutableIntStateOf(0) }
	Column(Modifier.onLeftRight { tab = (tab + it + titles.size) % titles.size }) {
		TabBar(titles, selected = tab)
		Panel(modifier = Modifier.size(50, 13)) {
			when (tab) {
				0 -> Column {
					Labeled("host") { Text("build-01.example") }
					Labeled("uptime") { Text("12d 04:11:52") }
					Labeled("load") { Text("0.42  0.38  0.35") }
					Spacer(Modifier.height(1))
					Labeled("cpu") { ProgressBar(0.42f, width = 24) }
					Labeled("memory") { ProgressBar(0.71f, width = 24, color = palette.warn) }
					Labeled("disk") { ProgressBar(0.93f, width = 24, color = palette.bad) }
				}

				1 -> ProcessTable(sortColumn = 2, limit = 8)

				else -> Column {
					for ((level, message) in logLines) {
						Row {
							val color = when (level) {
								"ERROR" -> palette.bad
								"WARN" -> palette.warn
								else -> palette.muted
							}
							Text(level.padEnd(6), color = color, textStyle = TextStyle.Bold)
							Text(message)
						}
					}
				}
			}
		}
	}
}

private val logLines = listOf(
	"INFO" to "gradle daemon started",
	"INFO" to "configuring :samples:catalog",
	"WARN" to "zig toolchain not cached, downloading",
	"INFO" to "compiling 9 Kotlin files",
	"ERROR" to "terminal too small: 72x20",
	"INFO" to "retrying with 80x24",
	"INFO" to "BUILD SUCCESSFUL in 41s",
)

private class Process(val pid: Int, val name: String, val cpu: Double, val memoryMb: Int)

private val processes = listOf(
	Process(812, "kotlin-daemon", 31.4, 2210),
	Process(1, "launchd", 0.2, 18),
	Process(4410, "java (gradle)", 54.9, 3140),
	Process(377, "WindowServer", 12.1, 640),
	Process(9021, "catalog", 3.7, 120),
	Process(2210, "zsh", 0.0, 6),
	Process(5120, "tmux: server", 0.8, 14),
	Process(733, "mds_stores", 6.2, 210),
	Process(6600, "Safari", 9.8, 1400),
	Process(3011, "ssh-agent", 0.1, 4),
)

private val processHeaders = listOf("PID", "NAME", "CPU%", "MEM MB")

@Composable
private fun ProcessTable(sortColumn: Int, limit: Int = processes.size) {
	val sorted = when (sortColumn) {
		0 -> processes.sortedBy { it.pid }
		1 -> processes.sortedBy { it.name.lowercase() }
		2 -> processes.sortedByDescending { it.cpu }
		else -> processes.sortedByDescending { it.memoryMb }
	}
	Table(
		headers = processHeaders,
		rows = sorted.take(limit).map {
			val tenths = (it.cpu * 10).roundToInt()
			listOf(it.pid.toString(), it.name, "${tenths / 10}.${tenths % 10}", it.memoryMb.toString())
		},
		alignEnd = setOf(0, 2, 3),
		highlightColumn = sortColumn,
	)
}

@Composable
fun TablePage() {
	val palette = LocalPalette.current
	var sort by remember { mutableIntStateOf(2) }
	Column(
		Modifier.onKeyEvent {
			if (it == KeyEvent(" ")) {
				sort = (sort + 1) % processHeaders.size
				true
			} else {
				false
			}
		},
	) {
		ProcessTable(sortColumn = sort)
		Spacer(Modifier.height(1))
		Text("Sorted by ${processHeaders[sort]}. Column widths come from", color = palette.muted)
		Text("the content; numeric columns are right-aligned.", color = palette.muted)
	}
}

private val fruits = listOf(
	"apple", "banana", "cherry", "damson", "elderberry", "fig", "grape", "huckleberry",
	"jackfruit", "kiwi", "lime", "mango", "nectarine",
)

@Composable
fun ScrollingListPage() {
	val palette = LocalPalette.current
	val count = 10_000
	var selected by remember { mutableIntStateOf(0) }
	val state = rememberLazyListState()
	// Keep the selection in view, scrolling as little as possible.
	LaunchedEffect(selected) {
		when {
			selected < state.firstVisibleItemIndex -> state.scrollToItem(selected)
			selected > state.lastVisibleItemIndex -> state.scrollToItem(selected - state.viewportSize + 1)
		}
	}
	Column(
		Modifier.onKeyEvent {
			when (it) {
				KeyEvent("j") -> selected = (selected + 1).coerceAtMost(count - 1)
				KeyEvent("k") -> selected = (selected - 1).coerceAtLeast(0)
				KeyEvent("g") -> selected = 0
				KeyEvent("G"), KeyEvent("g", shift = true) -> selected = count - 1
				else -> return@onKeyEvent false
			}
			true
		},
	) {
		Panel(modifier = Modifier.width(40), title = "${selected + 1} of $count") {
			LazyColumn(modifier = Modifier.height(12), state = state) {
				items(count) { index ->
					val item = "Item ${(index + 1).toString().padStart(5, '0')}  ${fruits[index % fruits.size]}"
					if (index == selected) {
						Box(Modifier.fillMaxWidth().background(palette.accentContainer)) {
							Text("▸ $item", color = palette.accent, textStyle = TextStyle.Bold)
						}
					} else {
						Text("  $item")
					}
				}
			}
		}
		Spacer(Modifier.height(1))
		Text("LazyColumn composes only the visible rows of its", color = palette.muted)
		Text("10,000 items. j/k move, g/G jump.", color = palette.muted)
	}
}

@Composable
fun LimitationsPage() {
	val palette = LocalPalette.current
	Column {
		Text("Fixed in this fork", textStyle = TextStyle.Bold)
		Text("Wide characters take two columns, so boxes line up:", color = palette.muted)
		Row {
			Panel(color = palette.good) { Text("中文宽字符") }
			Spacer(Modifier.width(2))
			Panel(color = palette.good) { Text("ascii") }
		}
		for (fix in fixes) {
			Row {
				Text("• ", color = palette.good)
				Text(fix)
			}
		}
		Spacer(Modifier.height(1))
		Text("Still missing", textStyle = TextStyle.Bold)
		for (gap in gaps) {
			Row {
				Text("• ", color = palette.warn)
				Text(gap)
			}
		}
	}
}

private val fixes = listOf(
	"Typing CJK through an input method works (#972)",
	"Shift+Enter, pastes, and the IME caret work",
	"Resizing the terminal no longer crashes (#1167)",
	"Text wraps to its constraints, nodes can clip",
	"Focus, scrolling, LazyColumn, Popup, alt screen",
	"println shows above the display (#681)",
	"Borders, images, and pointer shapes",
)

private val gaps = listOf(
	"Mouse events never reach composables (#495)",
)
