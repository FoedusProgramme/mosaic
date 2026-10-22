package org.unitedsoftware.mosaic.markdown

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.UnderlineStyle

/** The styles [Markdown] renders with. */
@Immutable
@Poko
public class MarkdownTheme(
	/** The style of headings of each level from 1 to 6. */
	public val headings: List<SpanStyle> = listOf(
		SpanStyle(textStyle = TextStyle.Bold, underlineStyle = UnderlineStyle.Straight),
		SpanStyle(textStyle = TextStyle.Bold),
		SpanStyle(textStyle = TextStyle.Bold),
		SpanStyle(textStyle = TextStyle.Bold + TextStyle.Italic),
		SpanStyle(textStyle = TextStyle.Italic),
		SpanStyle(textStyle = TextStyle.Italic + TextStyle.Dim),
	),
	public val emphasis: SpanStyle = SpanStyle(textStyle = TextStyle.Italic),
	public val strong: SpanStyle = SpanStyle(textStyle = TextStyle.Bold),
	public val strikethrough: SpanStyle = SpanStyle(textStyle = TextStyle.Strikethrough),
	public val code: SpanStyle = SpanStyle(color = Color(0x61, 0xAF, 0xEF)),
	/** Applied to links along with their URL. */
	public val link: SpanStyle = SpanStyle(color = Color(0x61, 0xAF, 0xEF), underlineStyle = UnderlineStyle.Straight),
	/** The color of the bar left of quotes. */
	public val quoteBar: Color = Color(0x5C, 0x63, 0x70),
	public val quote: SpanStyle = SpanStyle(textStyle = TextStyle.Italic),
	/** The bullets of unordered lists, used in turn for each nesting level. */
	public val bullets: List<String> = listOf("•", "◦", "▪"),
	public val listMarker: SpanStyle = SpanStyle(color = Color(0x5C, 0x63, 0x70)),
	public val checkedTask: String = "☒",
	public val uncheckedTask: String = "☐",
	public val tableBorder: Color = Color(0x5C, 0x63, 0x70),
	public val tableHeader: SpanStyle = SpanStyle(textStyle = TextStyle.Bold),
	public val thematicBreak: Color = Color(0x5C, 0x63, 0x70),
	public val syntax: SyntaxTheme = SyntaxTheme(),
) {
	public companion object {
		public val Default: MarkdownTheme = MarkdownTheme()
	}
}

/** The styles of highlighted code in fenced code blocks. */
@Immutable
@Poko
public class SyntaxTheme(
	public val plain: SpanStyle = SpanStyle(),
	public val keyword: SpanStyle = SpanStyle(color = Color(0xC6, 0x78, 0xDD)),
	public val string: SpanStyle = SpanStyle(color = Color(0x98, 0xC3, 0x79)),
	public val number: SpanStyle = SpanStyle(color = Color(0xD1, 0x9A, 0x66)),
	public val comment: SpanStyle = SpanStyle(color = Color(0x7F, 0x84, 0x8E), textStyle = TextStyle.Italic),
	public val type: SpanStyle = SpanStyle(color = Color(0xE5, 0xC0, 0x7B)),
	public val function: SpanStyle = SpanStyle(color = Color(0x61, 0xAF, 0xEF)),
	public val annotation: SpanStyle = SpanStyle(color = Color(0xE5, 0xC0, 0x7B)),
	public val inserted: SpanStyle = SpanStyle(color = Color(0x98, 0xC3, 0x79)),
	public val deleted: SpanStyle = SpanStyle(color = Color(0xE0, 0x6C, 0x75)),
	public val hunk: SpanStyle = SpanStyle(color = Color(0x56, 0xB6, 0xC2)),
	/** The context lines and file headers of diffs. */
	public val meta: SpanStyle = SpanStyle(textStyle = TextStyle.Dim),
)
