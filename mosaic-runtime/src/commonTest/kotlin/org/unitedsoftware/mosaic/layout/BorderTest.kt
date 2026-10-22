package org.unitedsoftware.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Text

class BorderTest {
	@Test fun wrapsTheContent() = runTest {
		runMosaicTest {
			setContent {
				Text("hi", modifier = Modifier.border())
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|╭──╮
				|│hi│
				|╰──╯
				""".trimMargin(),
			)
		}
	}

	@Test fun titleIsCutOffToFit() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(8, 3).border(BorderStyle.Ascii, title = "a long title")) {
					Text("x")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|+ a lo +
				||x     |
				|+------+
				""".trimMargin(),
			)
		}
	}

	@Test fun contentWrapsWithinTheBorder() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(7).border(BorderStyle.Single)) {
					Text("one two")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|┌─────┐
				|│one  │
				|│two  │
				|└─────┘
				""".trimMargin(),
			)
		}
	}

	@Test fun intrinsicsIncludeTheBorder() = runTest {
		runMosaicTest {
			setContent {
				Column(modifier = Modifier.width(IntrinsicSize.Max)) {
					Text("abc", modifier = Modifier.border(BorderStyle.Double))
					Text("-", modifier = Modifier.fillMaxWidth())
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|╔═══╗
				|║abc║
				|╚═══╝
				|-
				""".trimMargin(),
			)
		}
	}
}
