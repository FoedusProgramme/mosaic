package org.unitedsoftware.mosaic.markdown

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test

class BlocksTest {
	@Test fun headingsAndParagraphs() {
		assertThat(parseBlocks("# Title #\n\nSome\ntext\n## Sub")).containsExactly(
			Heading(1, "Title"),
			Paragraph("Some\ntext"),
			Heading(2, "Sub"),
		)
	}

	@Test fun fencedCode() {
		assertThat(parseBlocks("```kotlin\nval x = 1\n\nval y = 2\n```\nafter")).containsExactly(
			CodeBlock("kotlin", "val x = 1\n\nval y = 2", closed = true),
			Paragraph("after"),
		)
	}

	@Test fun unclosedFenceRunsToTheEnd() {
		assertThat(parseBlocks("text\n~~~\nstill code\n# not a heading")).containsExactly(
			Paragraph("text"),
			CodeBlock("", "still code\n# not a heading", closed = false),
		)
	}

	@Test fun closingFenceNeedsTheSameCharacterAndLength() {
		assertThat(parseBlocks("````\n```\n````")).containsExactly(CodeBlock("", "```", closed = true))
	}

	@Test fun bulletListWithNesting() {
		val blocks = parseBlocks("- one\n- two\n  - nested\n- three")
		assertThat(blocks).containsExactly(
			ListBlock(
				start = null,
				items = listOf(
					ListItem(listOf(Paragraph("one")), null),
					ListItem(
						listOf(
							Paragraph("two"),
							ListBlock(null, listOf(ListItem(listOf(Paragraph("nested")), null)), loose = false),
						),
						null,
					),
					ListItem(listOf(Paragraph("three")), null),
				),
				loose = false,
			),
		)
	}

	@Test fun orderedLooseListAndTasks() {
		assertThat(parseBlocks("3. [x] done\n\n4. [ ] todo")).containsExactly(
			ListBlock(
				start = 3,
				items = listOf(
					ListItem(listOf(Paragraph("done")), checked = true),
					ListItem(listOf(Paragraph("todo")), checked = false),
				),
				loose = true,
			),
		)
	}

	@Test fun listContinuationLines() {
		assertThat(parseBlocks("- first\nlazy\n- second\n\n  more")).containsExactly(
			ListBlock(
				start = null,
				items = listOf(
					ListItem(listOf(Paragraph("first\nlazy")), null),
					ListItem(listOf(Paragraph("second"), Paragraph("more")), null),
				),
				loose = true,
			),
		)
	}

	@Test fun onlyListsStartingWithOneInterruptParagraphs() {
		assertThat(parseBlocks("The year\n1999. was good")).containsExactly(Paragraph("The year\n1999. was good"))
		assertThat(parseBlocks("Steps:\n1. one")).containsExactly(
			Paragraph("Steps:"),
			ListBlock(1, listOf(ListItem(listOf(Paragraph("one")), null)), loose = false),
		)
	}

	@Test fun quotes() {
		assertThat(parseBlocks("> quoted\nlazy\n> # heading")).containsExactly(
			Quote(listOf(Paragraph("quoted\nlazy"), Heading(1, "heading"))),
		)
	}

	@Test fun thematicBreakIsNotAList() {
		assertThat(parseBlocks("a\n\n* * *\n\nb")).containsExactly(Paragraph("a"), ThematicBreak, Paragraph("b"))
	}

	@Test fun tables() {
		val blocks = parseBlocks("| a | b | c |\n|:--|:-:|--:|\n| 1 | `x|y` | 3 |\n| 4 |")
		assertThat(blocks).containsExactly(
			Table(
				header = listOf("a", "b", "c"),
				alignments = listOf(ColumnAlignment.Start, ColumnAlignment.Center, ColumnAlignment.End),
				rows = listOf(listOf("1", "`x|y`", "3"), listOf("4", "", "")),
			),
		)
	}

	@Test fun tableWithoutItsDelimiterRowIsAParagraph() {
		// While streaming, the delimiter row is not complete yet.
		assertThat(parseBlocks("| a | b |\n| -")).containsExactly(Paragraph("| a | b |\n| -"))
	}

	@Test fun tabsExpandToFourColumns() {
		assertThat(parseBlocks("-\tone")).isEqualTo(
			listOf(ListBlock(null, listOf(ListItem(listOf(Paragraph("one")), null)), loose = false)),
		)
	}
}
