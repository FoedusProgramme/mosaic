package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import org.unitedsoftware.mosaic.LocalTerminalState
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.fillMaxHeight
import org.unitedsoftware.mosaic.layout.fillMaxWidth
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.runMosaicMain
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle

/**
 * Every page must fit in the content area of a minimum-size terminal: 52 columns by 18 rows.
 * Mosaic does not clip, and drawing outside of the terminal crashes.
 */
private const val MinColumns = 80
private const val MinRows = 24
private const val SidebarWidth = 24

class Page(
	val section: String,
	val title: String,
	val summary: String,
	val hints: List<Pair<String, String>> = emptyList(),
	val content: @Composable () -> Unit,
)

private val pages = listOf(
	Page("Text", "Text styles", "TextStyle flags and UnderlineStyle variants.") { TextStylesPage() },
	Page("Text", "Colors", "Named colors, 24-bit color, and theme awareness.") { ColorsPage() },
	Page("Text", "Rich text", "AnnotatedString spans, nested with withStyle.") { RichTextPage() },
	Page("Text", "Markdown", "mosaic-markdown rendering a response as it streams in.", listOf("space" to "restart")) { MarkdownPage() },
	Page("Layout", "Row & Column", "Arrangement along the main axis.", listOf("←→" to "arrangement")) { ArrangementPage() },
	Page("Layout", "Box & Alignment", "Box(contentAlignment) with all nine alignments.") { AlignmentPage() },
	Page("Layout", "Modifiers", "Modifier order matters: each wraps the next.") { ModifiersPage() },
	Page("Layout", "Size & Weight", "weight, fillMaxWidth(fraction), and widthIn.", listOf("←→" to "resize")) { SizeWeightPage() },
	Page("Layout", "Intrinsics", "IntrinsicSize.Max and .Min to match siblings.") { IntrinsicsPage() },
	Page("Layout", "Custom layout", "A FlowRow written with Layout + MeasurePolicy.", listOf("←→" to "resize")) { CustomLayoutPage() },
	Page("Drawing", "Spacer & Filler", "Empty space and character fills.") { SpacerFillerPage() },
	Page("Drawing", "DrawScope", "drawBehind with drawRect and drawText.") { DrawScopePage() },
	Page("Input", "Keyboard", "Raw KeyEvents as delivered to onPreviewKeyEvent.") { KeyboardPage() },
	Page("Input", "Text input", "Recipe: focus handling and editable text fields.", listOf("tab" to "focus", "enter" to "submit")) { TextInputPage() },
	Page("Animation", "Animation", "Infinite transitions, easings, and springs.", listOf("space" to "spring")) { AnimationPage() },
	Page("Terminal", "Terminal state", "LocalTerminalState and the focus lifecycle.") { TerminalStatePage() },
	Page("Terminal", "Static output", "Lines written once above the live region.", listOf("enter" to "log a line", "p" to "println")) { StaticOutputPage() },
	Page("Recipes", "Panels", "Box-drawing borders built with drawBehind.") { PanelsPage() },
	Page("Recipes", "Progress", "Progress bars and spinners.") { ProgressPage() },
	Page("Recipes", "Tabs", "A tab bar driven by key events.", listOf("←→" to "tab")) { TabsPage() },
	Page("Recipes", "Table", "Column-aligned table with a sort key.", listOf("space" to "sort")) { TablePage() },
	Page("Recipes", "Scrolling list", "Windowed list with a scrollbar.", listOf("j/k" to "move")) { ScrollingListPage() },
	Page("Gaps", "Limitations", "What Mosaic cannot do yet.") { LimitationsPage() },
)

fun main(args: Array<String>) = runMosaicMain(alternateScreen = "--fullscreen" in args) {
	var exit by remember { mutableStateOf(false) }
	if (!exit) {
		val palette = when (LocalTerminalState.current.theme) {
			Terminal.Theme.Light -> LightPalette
			else -> DarkPalette
		}
		CompositionLocalProvider(LocalPalette provides palette) {
			Catalog(onExit = { exit = true })
		}
		LaunchedEffect(Unit) {
			awaitCancellation()
		}
	}
}

@Composable
private fun Catalog(onExit: () -> Unit) {
	val size = LocalTerminalState.current.size
	var selected by remember { mutableIntStateOf(0) }
	val keys = Modifier.onKeyEvent { event ->
		when (event) {
			KeyEvent("ArrowUp") -> selected = (selected - 1 + pages.size) % pages.size
			KeyEvent("ArrowDown") -> selected = (selected + 1) % pages.size
			KeyEvent("q"), KeyEvent("Escape") -> onExit()
			else -> return@onKeyEvent false
		}
		true
	}

	if (size.columns < MinColumns || size.rows < MinRows) {
		Text(
			"Needs ${MinColumns}x$MinRows, now ${size.columns}x${size.rows}. q quits.",
			modifier = keys,
			color = LocalPalette.current.warn,
		)
		return
	}

	val page = pages[selected]
	// Leave the last terminal row free since every rendered row ends with a newline.
	Column(modifier = keys.size(size.columns, size.rows - 1)) {
		Header(page)
		Row(modifier = Modifier.weight(1f)) {
			Sidebar(
				selected = selected,
				height = size.rows - 3,
				modifier = Modifier.width(SidebarWidth).fillMaxHeight(),
			)
			Filler('│', modifier = Modifier.width(1).fillMaxHeight(), foreground = LocalPalette.current.muted)
			Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(left = 2, right = 1)) {
				Text(page.title, color = LocalPalette.current.accent, textStyle = TextStyle.Bold)
				Text(page.summary, color = LocalPalette.current.muted)
				Spacer(Modifier.height(1))
				key(page) {
					page.content()
				}
			}
		}
		Footer(page)
	}
}

@Composable
private fun Header(page: Page) {
	val palette = LocalPalette.current
	Row {
		Text(" Mosaic Catalog ", color = palette.surface, background = palette.accent, textStyle = TextStyle.Bold)
		Text("  ${pages.indexOf(page) + 1}/${pages.size}  ${page.section} › ${page.title}", color = palette.muted)
	}
}

@Composable
private fun Footer(page: Page) {
	val palette = LocalPalette.current
	val hints = listOf("↑↓" to "page") + page.hints + listOf("q" to "quit")
	Box(Modifier.fillMaxWidth().background(palette.surface)) {
		Text(
			buildAnnotatedString {
				hints.forEach { (key, label) ->
					append(" ")
					withStyle(SpanStyle(color = palette.accent, textStyle = TextStyle.Bold)) { append(key) }
					append(" ")
					append(label)
					append("  ")
				}
			},
			color = palette.muted,
		)
	}
}

private sealed interface SidebarEntry {
	class Header(val section: String) : SidebarEntry
	class Item(val index: Int, val page: Page) : SidebarEntry
}

private val sidebarEntries: List<SidebarEntry> = buildList {
	var section = ""
	pages.forEachIndexed { index, page ->
		if (page.section != section) {
			section = page.section
			add(SidebarEntry.Header(section))
		}
		add(SidebarEntry.Item(index, page))
	}
}

@Composable
private fun Sidebar(
	selected: Int,
	height: Int,
	modifier: Modifier = Modifier,
) {
	val palette = LocalPalette.current
	val selectedEntry = sidebarEntries.indexOfFirst { it is SidebarEntry.Item && it.index == selected }
	ScrollingList(sidebarEntries, selected = selectedEntry, height = height, modifier = modifier) { entry, isSelected ->
		when (entry) {
			is SidebarEntry.Header -> Text(" ${entry.section.uppercase()}", color = palette.muted, textStyle = TextStyle.Bold)

			is SidebarEntry.Item -> if (isSelected) {
				Box(Modifier.fillMaxWidth().background(palette.accentContainer)) {
					Text(" ▸ ${entry.page.title}", color = palette.accent, textStyle = TextStyle.Bold)
				}
			} else {
				Text("   ${entry.page.title}", color = palette.text)
			}
		}
	}
}
