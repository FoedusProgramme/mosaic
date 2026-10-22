package org.unitedsoftware.mosaic.markdown

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.ui.Color

class HighlightingTest {
	// Distinct colors, since some of the default styles are the same.
	private val theme = (1..11).map { SpanStyle(color = Color(it, 0, 0)) }.let {
		SyntaxTheme(
			keyword = it[0],
			string = it[1],
			number = it[2],
			comment = it[3],
			type = it[4],
			function = it[5],
			annotation = it[6],
			inserted = it[7],
			deleted = it[8],
			hunk = it[9],
			meta = it[10],
		)
	}

	private fun AnnotatedString.spans() = spanStyles.map { range ->
		val kind = when (range.item) {
			theme.keyword -> "keyword"
			theme.string -> "string"
			theme.number -> "number"
			theme.comment -> "comment"
			theme.type -> "type"
			theme.function -> "function"
			theme.annotation -> "annotation"
			theme.inserted -> "inserted"
			theme.deleted -> "deleted"
			theme.hunk -> "hunk"
			theme.meta -> "meta"
			else -> "other"
		}
		"${text.substring(range.start, range.end)}:$kind"
	}

	@Test fun kotlin() {
		val code = "@Composable fun Greeting(name: String) = println(\"Hi \$name\") // 42\n/* done */ val x = 1.5"
		val highlighted = highlight(code, "kotlin", theme)
		assertThat(highlighted.text).isEqualTo(code)
		assertThat(highlighted.spans()).containsExactly(
			"@Composable:annotation",
			"fun:keyword",
			"Greeting:function",
			"String:type",
			"println:function",
			"\"Hi \$name\":string",
			"// 42:comment",
			"/* done */:comment",
			"val:keyword",
			"1.5:number",
		)
	}

	@Test fun pythonTripleQuotedStringsSpanLines() {
		val highlighted = highlight("def f():\n    \"\"\"a\nb\"\"\" # c", "py", theme)
		assertThat(highlighted.spans()).containsExactly("def:keyword", "f:function", "\"\"\"a\nb\"\"\":string", "# c:comment")
	}

	@Test fun shellVariablesAndComments() {
		val highlighted = highlight("echo \$HOME # home\nfoo#bar", "bash", theme)
		assertThat(highlighted.spans()).containsExactly("echo:keyword", "\$HOME:keyword", "# home:comment")
	}

	@Test fun diff() {
		val highlighted = highlight("--- a/x\n+++ b/x\n@@ -1 +1 @@\n-old\n+new\n same", "diff", theme)
		assertThat(highlighted.spans()).containsExactly(
			"--- a/x:meta",
			"+++ b/x:meta",
			"@@ -1 +1 @@:hunk",
			"-old:deleted",
			"+new:inserted",
		)
	}

	@Test fun unknownLanguagesStillGetStrings() {
		assertThat(highlight("say \"hi\" 3", "klingon", theme).spans()).containsExactly("\"hi\":string", "3:number")
	}
}
