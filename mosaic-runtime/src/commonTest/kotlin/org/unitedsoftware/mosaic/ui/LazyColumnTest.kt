package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.testing.runMosaicTest

class LazyColumnTest {
	@Test fun onlyVisibleItemsAreComposed() = runTest {
		val composed = mutableListOf<Int>()
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(3)) {
					items(1_000_000) {
						composed += it
						Text("item $it")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("item 0\nitem 1\nitem 2")
			assertThat(composed).containsExactly(0, 1, 2)
		}
	}

	@Test fun heightFollowsContentUpToConstraints() = runTest {
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(5)) {
					item { Text("a") }
					item { Text("b") }
				}
			}
			// The fixed height pads the list below its items.
			assertThat(awaitSnapshot()).isEqualTo("a\nb\n\n\n")
		}
	}

	@Test fun scrollByRowsAcrossItemsOfDifferentHeights() = runTest {
		val state = LazyListState()
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(3), state = state) {
					item { Text("a1\na2") }
					item { Text("b1\nb2\nb3") }
					item { Text("c1") }
					item { Text("d1") }
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("a1\na2\nb1")

			state.scrollBy(1)
			assertThat(awaitSnapshot()).isEqualTo("a2\nb1\nb2")
			assertThat(state.firstVisibleItemIndex).isEqualTo(0)
			assertThat(state.firstVisibleItemScrollOffset).isEqualTo(1)

			state.scrollBy(2)
			assertThat(awaitSnapshot()).isEqualTo("b2\nb3\nc1")
			assertThat(state.firstVisibleItemIndex).isEqualTo(1)
			assertThat(state.firstVisibleItemScrollOffset).isEqualTo(1)

			// Scrolling past the end stops with the last item at the bottom.
			state.scrollBy(100)
			assertThat(awaitSnapshot()).isEqualTo("b3\nc1\nd1")
			assertThat(state.canScrollForward).isFalse()
			assertThat(state.canScrollBackward).isTrue()

			state.scrollBy(-100)
			assertThat(awaitSnapshot()).isEqualTo("a1\na2\nb1")
			assertThat(state.canScrollBackward).isFalse()
		}
	}

	@Test fun scrollToEndAndFollowNewItems() = runTest {
		val lines = mutableStateListOf("1", "2", "3", "4")
		val state = LazyListState()
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(2), state = state) {
					items(lines) { Text(it) }
				}
			}
			awaitSnapshot()
			state.scrollToEnd()
			assertThat(awaitSnapshot()).isEqualTo("3\n4")

			lines += "5"
			state.scrollToEnd()
			assertThat(awaitSnapshot()).isEqualTo("4\n5")
		}
	}

	@Test fun keyedItemsKeepTheirPositionAndState() = runTest {
		val names = mutableStateListOf("c", "d")
		val state = LazyListState()
		runMosaicTest {
			setContent {
				LazyColumn(modifier = Modifier.height(1), state = state) {
					items(names, key = { it }) { name ->
						var count by remember { mutableIntStateOf(0) }
						Text(
							"$name$count",
							modifier = Modifier.onKeyEvent {
								count++
								true
							},
						)
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("c0")
			sendKeyEvent(KeyboardEvent('x'.code))
			assertThat(awaitSnapshot()).isEqualTo("c1")

			// Items added before the first visible one do not move the list.
			names.add(0, "a")
			names.add(1, "b")
			awaitSnapshot()
			assertThat(state.firstVisibleItemIndex).isEqualTo(2)
			state.scrollBy(-1)
			assertThat(awaitSnapshot()).isEqualTo("b0")
			state.scrollBy(1)
			// The remembered count was released while "c" was scrolled out of view.
			assertThat(awaitSnapshot()).isEqualTo("c0")
		}
	}

	@Test fun itemStateChangesRedraw() = runTest {
		var text by mutableStateOf("one")
		runMosaicTest {
			setContent {
				LazyColumn {
					item { Text(text) }
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("one")
			text = "two"
			assertThat(awaitSnapshot()).isEqualTo("two")
		}
	}

	@Test fun itemsAreDisposedWithTheList() = runTest {
		var show by mutableStateOf(true)
		val disposed = mutableListOf<Int>()
		runMosaicTest {
			setContent {
				Column {
					if (show) {
						LazyColumn {
							items(2) {
								DisposableEffect(Unit) { onDispose { disposed += it } }
								Text("$it")
							}
						}
					}
					Text("end")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("0\n1\nend")
			show = false
			assertThat(awaitSnapshot()).isEqualTo("end")
			assertThat(disposed).containsExactly(0, 1)
		}
	}
}
