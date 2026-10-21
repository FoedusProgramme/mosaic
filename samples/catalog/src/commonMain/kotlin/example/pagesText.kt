package example

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.text.withStyle
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.UnderlineStyle
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.floor

private const val Sample = "The quick brown fox"

@Composable
fun TextStylesPage() {
	val palette = LocalPalette.current
	Column {
		Labeled("Unspecified") { Text(Sample) }
		Labeled("Bold") { Text(Sample, textStyle = TextStyle.Bold) }
		Labeled("Dim") { Text(Sample, textStyle = TextStyle.Dim) }
		Labeled("Italic") { Text(Sample, textStyle = TextStyle.Italic) }
		Labeled("Strikethrough") { Text(Sample, textStyle = TextStyle.Strikethrough) }
		Labeled("Invert") { Text(Sample, textStyle = TextStyle.Invert) }
		Labeled("Bold + Italic") { Text(Sample, textStyle = TextStyle.Bold + TextStyle.Italic) }
		Spacer(Modifier.height(1))
		Labeled("Straight") { Text(Sample, underlineStyle = UnderlineStyle.Straight) }
		Labeled("Double") { Text(Sample, underlineStyle = UnderlineStyle.Double, underlineColor = palette.accent) }
		Labeled("Curly") { Text(Sample, underlineStyle = UnderlineStyle.Curly, underlineColor = palette.bad) }
		Labeled("Dotted") { Text(Sample, underlineStyle = UnderlineStyle.Dotted, underlineColor = palette.good) }
		Labeled("Dashed") { Text(Sample, underlineStyle = UnderlineStyle.Dashed, underlineColor = palette.warn) }
		Spacer(Modifier.height(1))
		Text("Styled underlines need a Kitty-compatible terminal;", color = palette.muted)
		Text("elsewhere they fall back to a straight underline.", color = palette.muted)
	}
}

private val namedColors = listOf(
	"Red" to Color.Red,
	"Green" to Color.Green,
	"Blue" to Color.Blue,
	"Yellow" to Color.Yellow,
	"Magenta" to Color.Magenta,
	"Cyan" to Color.Cyan,
	"White" to Color.White,
	"Black" to Color.Black,
)

@Composable
fun ColorsPage() {
	val palette = LocalPalette.current
	Column {
		Text("Named colors", textStyle = TextStyle.Bold)
		for (chunk in namedColors.chunked(4)) {
			Row {
				for ((name, color) in chunk) {
					val foreground = if (color == Color.Blue || color == Color.Black) Color.White else Color.Black
					Text(" ${name.padEnd(8)}", color = foreground, background = color)
					Text(" ")
				}
			}
		}
		Spacer(Modifier.height(1))
		Text("24-bit color: hue × value, two pixels per cell (▀)", textStyle = TextStyle.Bold)
		Box(
			modifier = Modifier.size(48, 4).drawBehind {
				for (x in 0 until width) {
					for (y in 0 until height) {
						val hue = x.toFloat() / width
						val top = hsv(hue, 1f, 1f - (2 * y) / (2f * height))
						val bottom = hsv(hue, 1f, 1f - (2 * y + 1) / (2f * height))
						drawRect('▀', foreground = top, background = bottom, topLeft = IntOffset(x, y), size = IntSize(1, 1))
					}
				}
			},
		)
		Box(
			modifier = Modifier.size(48, 1).drawBehind {
				for (x in 0 until width) {
					val level = x.toFloat() / (width - 1)
					drawRect(' ', background = Color(level, level, level), topLeft = IntOffset(x, 0), size = IntSize(1, 1))
				}
			},
		)
		Spacer(Modifier.height(1))
		Text(
			buildAnnotatedString {
				append("Terminal theme ")
				withStyle(SpanStyle(color = palette.accent, textStyle = TextStyle.Bold)) {
					append(LocalTerminalState.current.theme.name)
				}
				append(" → ")
				append(if (palette === LightPalette) "light" else "dark")
				append(" catalog palette:")
			},
		)
		Row {
			Swatch("text", palette.text)
			Swatch("muted", palette.muted)
			Swatch("accent", palette.accent)
			Swatch("good", palette.good)
			Swatch("warn", palette.warn)
			Swatch("bad", palette.bad)
		}
	}
}

@Composable
private fun Swatch(name: String, color: Color) {
	Row {
		Text("■ ", color = color)
		Text("$name  ", color = LocalPalette.current.muted)
	}
}

fun hsv(hue: Float, saturation: Float, value: Float): Color {
	val h = (hue - floor(hue)) * 6f
	val c = value * saturation
	val x = c * (1 - abs(h % 2 - 1))
	val m = value - c
	val (r, g, b) = when (h.toInt()) {
		0 -> Triple(c, x, 0f)
		1 -> Triple(x, c, 0f)
		2 -> Triple(0f, c, x)
		3 -> Triple(0f, x, c)
		4 -> Triple(x, 0f, c)
		else -> Triple(c, 0f, x)
	}
	return Color(r + m, g + m, b + m)
}

private val code = listOf(
	"// Spans nest, and inner styles win.",
	"@Composable",
	"fun Greeting(name: String, count: Int = 3) {",
	"  val text = buildAnnotatedString {",
	"    append(\"Hello, \")",
	"    withStyle(SpanStyle(color = Color.Red)) {",
	"      append(name.repeat(count))",
	"    }",
	"  }",
	"  Text(text)",
	"}",
)

private val keywords = setOf("fun", "val", "var", "return", "if", "else", "when", "class", "private")
private val tokens = Regex("""//.*|"[^"]*"|@\w+|\b\d+\b|\b[A-Za-z_]\w*\b""")

/** A tiny regex highlighter: the kind of thing AnnotatedString is for. */
private fun highlight(line: String, palette: Palette): AnnotatedString = buildAnnotatedString {
	var last = 0
	for (match in tokens.findAll(line)) {
		append(line.substring(last, match.range.first))
		val token = match.value
		val style = when {
			token.startsWith("//") -> SpanStyle(color = palette.muted, textStyle = TextStyle.Italic)
			token.startsWith("\"") -> SpanStyle(color = palette.good)
			token.startsWith("@") -> SpanStyle(color = palette.warn)
			token[0].isDigit() -> SpanStyle(color = palette.bad)
			token in keywords -> SpanStyle(color = palette.accent, textStyle = TextStyle.Bold)
			token[0].isUpperCase() -> SpanStyle(color = palette.text, underlineStyle = UnderlineStyle.Dotted)
			else -> null
		}
		if (style != null) withStyle(style) { append(token) } else append(token)
		last = match.range.last + 1
	}
	append(line.substring(last))
}

@Composable
fun RichTextPage() {
	val palette = LocalPalette.current
	Column {
		Text(
			buildAnnotatedString {
				append("One Text, many spans: ")
				withStyle(SpanStyle(textStyle = TextStyle.Bold)) { append("bold, ") }
				withStyle(SpanStyle(textStyle = TextStyle.Italic)) { append("italic, ") }
				withStyle(SpanStyle(color = palette.surface, background = palette.warn)) { append("highlight") }
				append(",")
			},
		)
		Text(
			buildAnnotatedString {
				withStyle(SpanStyle(underlineStyle = UnderlineStyle.Curly, underlineColor = palette.bad)) {
					append("speling")
				}
				append(" mistakes, and ")
				withStyle(SpanStyle(color = palette.accent)) {
					append("colored ")
					withStyle(SpanStyle(textStyle = TextStyle.Bold)) { append("nested") }
					append(" spans")
				}
				append(".")
			},
		)
		Spacer(Modifier.height(1))
		Panel(title = "Greeting.kt") {
			Column {
				for (line in code) {
					Text(highlight(line, palette))
				}
			}
		}
	}
}
