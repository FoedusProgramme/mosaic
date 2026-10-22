package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.MouseEvent
import org.unitedsoftware.mosaic.terminal.MouseEvent.Button
import org.unitedsoftware.mosaic.terminal.MouseEvent.Type
import org.unitedsoftware.mosaic.testing.TestMosaic
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.LazyColumn
import org.unitedsoftware.mosaic.ui.Popup
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextField
import org.unitedsoftware.mosaic.ui.TextFieldState
import org.unitedsoftware.mosaic.ui.unit.IntOffset

class PointerInputTest {
	private fun TestMosaic<*>.click(x: Int, y: Int, releaseX: Int = x, releaseY: Int = y) {
		sendEvent(MouseEvent(x, y, Type.Press, Button.Left))
		sendEvent(MouseEvent(releaseX, releaseY, Type.Release, Button.Left))
	}

	private fun TestMosaic<*>.wheel(x: Int, y: Int, down: Boolean) {
		sendEvent(MouseEvent(x, y, Type.Press, if (down) Button.WheelDown else Button.WheelUp))
	}

	@Test fun click() = runTest {
		runMosaicTest {
			setContent {
				var count by remember { mutableIntStateOf(0) }
				Column {
					Text("[button]", modifier = Modifier.onClick { count++ })
					Text("clicked $count")
				}
			}
			awaitSnapshot()
			click(3, 0)
			assertThat(awaitSnapshot()).isEqualTo("[button]\nclicked 1")

			// Releasing outside of the node cancels the click.
			click(3, 0, releaseY = 1)
			click(3, 1)
			click(3, 0)
			assertThat(awaitSnapshot()).isEqualTo("[button]\nclicked 2")
		}
	}

	@Test fun innermostNodeFirstThenAncestorsWithRelativeCoordinates() = runTest {
		val received = mutableListOf<String>()
		runMosaicTest {
			setContent {
				var done by remember { mutableStateOf(false) }
				Column(
					modifier = Modifier.onPointerEvent {
						if (it.type != PointerEventType.Press) return@onPointerEvent false
						received += "outer ${it.x},${it.y}"
						done = true
						true
					},
				) {
					Text(if (done) "done" else "first")
					Text(
						"second",
						modifier = Modifier.padding(left = 2).onPointerEvent {
							if (it.type == PointerEventType.Press) received += "inner ${it.x},${it.y}"
							false
						},
					)
				}
			}
			awaitSnapshot()
			sendEvent(MouseEvent(4, 1, Type.Press, Button.Left))
			assertThat(awaitSnapshot()).isEqualTo("done\n  second")
			// The padding is inside of the modifier, so its coordinates include it.
			assertThat(received).containsExactly("inner 4,0", "outer 4,1")
		}
	}

	@Test fun hover() = runTest {
		runMosaicTest {
			setContent {
				var hovered by remember { mutableStateOf(false) }
				Text(if (hovered) "[hover]" else " hover ", modifier = Modifier.onHover { hovered = it })
			}
			awaitSnapshot()
			sendEvent(MouseEvent(2, 0, Type.Motion))
			assertThat(awaitSnapshot()).isEqualTo("[hover]")
			sendEvent(MouseEvent(20, 5, Type.Motion))
			assertThat(awaitSnapshot()).isEqualTo(" hover")
		}
	}

	@Test fun wheelScrollsTheInnermostScrollableContainer() = runTest {
		val outer = ScrollState()
		val inner = ScrollState()
		runMosaicTest {
			setContent {
				Column(modifier = Modifier.height(3).verticalScroll(outer)) {
					Column(modifier = Modifier.height(2).verticalScroll(inner)) {
						for (index in 1..5) Text("i$index")
					}
					for (index in 1..4) Text("o$index")
				}
			}
			awaitSnapshot()
			wheel(0, 0, down = true)
			assertThat(awaitSnapshot()).isEqualTo("i4\ni5\no1")
			assertThat(inner.value).isEqualTo(3)

			// The inner one is at its end, so the outer one scrolls instead.
			wheel(0, 0, down = true)
			assertThat(awaitSnapshot()).isEqualTo("o2\no3\no4")
			assertThat(outer.value).isEqualTo(3)
		}
	}

	@Test fun wheelScrollsLazyColumn() = runTest {
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(2)) {
					items(10) { Text("$it") }
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("0\n1")
			wheel(0, 1, down = true)
			assertThat(awaitSnapshot()).isEqualTo("3\n4")
			wheel(0, 1, down = false)
			assertThat(awaitSnapshot()).isEqualTo("0\n1")
		}
	}

	@Test fun clippedChildrenCannotBeClickedOutsideTheirParent() = runTest {
		runMosaicTest {
			setContent {
				var clicked by remember { mutableStateOf("none") }
				Column {
					Box(modifier = Modifier.size(2, 1).clipToBounds()) {
						Text("hidden", modifier = Modifier.width(6).onClick { clicked = "hidden" })
					}
					Text("clicked $clicked")
				}
			}
			awaitSnapshot()
			click(4, 0)
			click(1, 0)
			assertThat(awaitSnapshot()).isEqualTo("hi\nclicked hidden")
		}
	}

	@Test fun popupsGetEventsFirst() = runTest {
		runMosaicTest {
			setContent {
				var clicked by remember { mutableStateOf("none") }
				Column {
					Text("below...", modifier = Modifier.onClick { clicked = "below" })
					Popup(offset = IntOffset(0, -1)) {
						Text("pop", modifier = Modifier.onClick { clicked = "popup" })
					}
					Text("clicked $clicked")
				}
			}
			awaitSnapshot()
			click(1, 0)
			assertThat(awaitSnapshot()).isEqualTo("popow...\nclicked popup")
			click(6, 0)
			assertThat(awaitSnapshot()).isEqualTo("popow...\nclicked below")
		}
	}

	@Test fun clickFocusesAndPlacesTheTextFieldCursor() = runTest {
		val first = TextFieldState("abc")
		val second = TextFieldState("中文x")
		runMosaicTest {
			setContent {
				Row {
					TextField(first, modifier = Modifier.width(5))
					TextField(second, modifier = Modifier.width(6))
				}
			}
			awaitSnapshot()
			// The right half of the first wide character puts the cursor after it.
			click(6, 0)
			sendKeyEvent(KeyboardEvent('!'.code))
			assertThat(awaitSnapshot()).isEqualTo("abc  中!文x")
		}
	}
}
