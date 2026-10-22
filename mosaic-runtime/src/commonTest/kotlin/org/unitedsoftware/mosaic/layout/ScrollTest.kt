package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.mutableStateListOf
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text

class ScrollTest {
	@Test fun verticalScroll() = runTest {
		val state = ScrollState()
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.height(2).verticalScroll(state)) {
					Column {
						Text("1")
						Text("2")
						Text("3")
						Text("4")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("1\n2")
			assertThat(state.maxValue).isEqualTo(2)
			assertThat(state.viewportSize).isEqualTo(2)

			state.scrollBy(1)
			assertThat(awaitSnapshot()).isEqualTo("2\n3")

			state.scrollBy(10)
			assertThat(state.value).isEqualTo(2)
			assertThat(awaitSnapshot()).isEqualTo("3\n4")
		}
	}

	@Test fun scrollToEndFollowsGrowingContent() = runTest {
		val state = ScrollState()
		val lines = mutableStateListOf("1", "2", "3")
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.height(2).verticalScroll(state)) {
					Column {
						for (line in lines) Text(line)
					}
				}
			}
			awaitSnapshot()
			state.scrollToEnd()
			assertThat(awaitSnapshot()).isEqualTo("2\n3")

			lines += "4"
			state.scrollToEnd()
			assertThat(awaitSnapshot()).isEqualTo("3\n4")
		}
	}

	@Test fun horizontalScroll() = runTest {
		val state = ScrollState(2)
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(3).horizontalScroll(state)) {
					Row {
						Text("abcdef")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("cde")
		}
	}

	@Test fun contentSmallerThanViewportCannotScroll() = runTest {
		val state = ScrollState()
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.verticalScroll(state)) {
					Text("1")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("1")
			assertThat(state.maxValue).isEqualTo(0)
			assertThat(state.canScrollForward).isEqualTo(false)
		}
	}
}
