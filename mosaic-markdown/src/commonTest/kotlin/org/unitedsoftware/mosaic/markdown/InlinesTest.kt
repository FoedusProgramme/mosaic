package org.unitedsoftware.mosaic.markdown

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.ui.TextStyle

class InlinesTest {
	private val theme = MarkdownTheme.Default

	private fun parse(text: String) = parseInlines(text, theme)

	/** The styled parts of [this] as "text:style" pairs. */
	private fun AnnotatedString.spans() = spanStyles.map { range ->
		val style = range.item
		val kind = when {
			style.link != null -> "link=${style.link}"
			style == theme.code -> "code"
			TextStyle.Bold in style.textStyle && TextStyle.Italic in style.textStyle -> "bold+italic"
			TextStyle.Bold in style.textStyle -> "bold"
			TextStyle.Italic in style.textStyle -> "italic"
			TextStyle.Strikethrough in style.textStyle -> "strike"
			else -> "other"
		}
		"${text.substring(range.start, range.end)}:$kind"
	}

	@Test fun emphasis() {
		val parsed = parse("*a* **b** ***c*** ~~d~~ _e_")
		assertThat(parsed.text).isEqualTo("a b c d e")
		assertThat(parsed.spans()).containsExactly("a:italic", "b:bold", "c:bold", "c:bold+italic", "d:strike", "e:italic")
	}

	@Test fun nestedStrongInsideEmphasis() {
		val parsed = parse("*a **b** c*")
		assertThat(parsed.text).isEqualTo("a b c")
		assertThat(parsed.spans()).containsExactly("a b c:italic", "b:bold+italic")
	}

	@Test fun nestedEmphasisCombines() {
		val parsed = parse("**bold *both***")
		assertThat(parsed.text).isEqualTo("bold both")
		assertThat(parsed.spans()).containsExactly("bold both:bold", "both:bold+italic")
	}

	@Test fun unclosedDelimitersAreLiteral() {
		assertThat(parse("**still streaming").text).isEqualTo("**still streaming")
		assertThat(parse("a * b").text).isEqualTo("a * b")
		assertThat(parse("`open").text).isEqualTo("`open")
	}

	@Test fun underscoresInWordsAreNotEmphasis() {
		val parsed = parse("snake_case_name")
		assertThat(parsed.text).isEqualTo("snake_case_name")
		assertThat(parsed.spans()).containsExactly()
	}

	@Test fun codeSpans() {
		val parsed = parse("run `` a`b `` and `*x*`")
		assertThat(parsed.text).isEqualTo("run a`b and *x*")
		assertThat(parsed.spans()).containsExactly("a`b:code", "*x*:code")
	}

	@Test fun links() {
		val parsed = parse("see [the *docs*](https://example.com/a_(b) \"Title\") or <https://x.org>")
		assertThat(parsed.text).isEqualTo("see the docs or https://x.org")
		assertThat(parsed.spans()).containsExactly(
			"the docs:link=https://example.com/a_(b)",
			// Spans inside the link keep linking, since only spans with a link change it.
			"docs:italic",
			"https://x.org:link=https://x.org",
		)
	}

	@Test fun bareUrlsLeaveTrailingPunctuation() {
		val parsed = parse("Visit https://example.com/path. Or (https://example.com/x).")
		assertThat(parsed.spans()).containsExactly(
			"https://example.com/path:link=https://example.com/path",
			"https://example.com/x:link=https://example.com/x",
		)
	}

	@Test fun images() {
		val parsed = parse("![a cat](cat.png)")
		assertThat(parsed.text).isEqualTo("🖼 a cat")
	}

	@Test fun escapesAndLineBreaks() {
		assertThat(parse("\\*not\\* soft\nbreak  \nhard\\\nhard").text).isEqualTo("*not* soft break\nhard\nhard")
	}

	@Test fun manyUnclosedDelimitersAreFast() {
		val text = (1..2000).joinToString(" ") { "*w$it" }
		assertThat(parse(text).text).isEqualTo(text)
	}
}
