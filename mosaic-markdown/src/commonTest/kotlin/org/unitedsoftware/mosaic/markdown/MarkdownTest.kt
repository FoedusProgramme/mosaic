package org.unitedsoftware.mosaic.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box

class MarkdownTest {
	private suspend fun render(markdown: String, width: Int = 30): String {
		var snapshot = ""
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(width)) {
					Markdown(markdown)
				}
			}
			snapshot = awaitSnapshot()
		}
		return snapshot
	}

	@Test fun blocksAreSeparatedByBlankLines() = runTest {
		assertThat(render("# Title\nSome *text* that wraps at the width of the box.\n\n---")).isEqualTo(
			"""
			|Title
			|
			|Some text that wraps at the
			|width of the box.
			|
			|──────────────────────────────
			""".trimMargin(),
		)
	}

	@Test fun lists() = runTest {
		assertThat(render("- one\n- two that is long enough to wrap\n  - nested\n\n1. [x] done\n2. todo")).isEqualTo(
			"""
			|• one
			|• two that is long enough to
			|  wrap
			|  ◦ nested
			|
			|1. ☒ done
			|2. todo
			""".trimMargin(),
		)
	}

	@Test fun bulletTasks() = runTest {
		assertThat(render("- [ ] open\n- [x] done")).isEqualTo("☐ open\n☒ done")
	}

	@Test fun quotes() = runTest {
		assertThat(render("> quoted text\n>\n> more", width = 12)).isEqualTo(
			"""
			|│ quoted
			|│ text
			|│
			|│ more
			""".trimMargin(),
		)
	}

	@Test fun codeBlocks() = runTest {
		assertThat(render("```diff\n-old\n+new\n```\n\n```\nunclosed")).isEqualTo(
			"""
			|  -old
			|  +new
			|
			|  unclosed
			""".trimMargin(),
		)
	}

	@Test fun tables() = runTest {
		assertThat(render("| Name | Count |\n|:-----|------:|\n| a | 1 |\n| bb | 22 |")).isEqualTo(
			"""
			|┌──────┬───────┐
			|│ Name │ Count │
			|├──────┼───────┤
			|│ a    │     1 │
			|│ bb   │    22 │
			|└──────┴───────┘
			""".trimMargin(),
		)
	}

	@Test fun tablesNarrowTheirWidestColumnToFit() = runTest {
		assertThat(render("| a | b |\n|---|---|\n| short | a much longer cell |", width = 20)).isEqualTo(
			"""
			|┌───────┬──────────┐
			|│ a     │ b        │
			|├───────┼──────────┤
			|│ short │ a much   │
			|│       │ longer   │
			|│       │ cell     │
			|└───────┴──────────┘
			""".trimMargin(),
		)
	}

	@Test fun streamingUpdates() = runTest {
		var text by mutableStateOf("Hello **wor")
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(30)) {
					Markdown(text)
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("Hello **wor")
			text = "Hello **world**\n\n```kotlin\nval x"
			assertThat(awaitSnapshot()).isEqualTo("Hello world\n\n  val x")
		}
	}
}
