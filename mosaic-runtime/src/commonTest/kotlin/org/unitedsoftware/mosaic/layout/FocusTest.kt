package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierShift
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text

class FocusTest {
	private val tab = KeyboardEvent(0x09)
	private val shiftTab = KeyboardEvent(0x09, modifiers = ModifierShift)

	@Composable
	private fun Item(name: String, modifier: Modifier = Modifier) {
		var focused by remember { mutableStateOf(false) }
		Text(
			if (focused) "[$name]" else " $name ",
			modifier = modifier
				.onFocusChanged { focused = it.isFocused }
				.focusable(),
		)
	}

	@Test fun firstFocusableIsFocusedAndTabMoves() = runTest {
		runMosaicTest {
			setContent {
				Row {
					Item("a")
					Item("b")
					Item("c")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(" a  b  c")
			// The first focusable is focused after the first layout, and the item recomposes for it.
			assertThat(awaitSnapshot()).isEqualTo("[a] b  c")

			sendKeyEvent(tab)
			assertThat(awaitSnapshot()).isEqualTo(" a [b] c")

			sendKeyEvent(shiftTab)
			sendKeyEvent(shiftTab)
			// Moving back from the first wraps around to the last.
			assertThat(awaitSnapshot()).isEqualTo(" a  b [c]")
		}
	}

	@Test fun focusRequester() = runTest {
		val requester = FocusRequester()
		runMosaicTest {
			setContent {
				Row {
					Item("a")
					Item("b", modifier = Modifier.focusRequester(requester))
				}
				LaunchedEffect(Unit) {
					requester.requestFocus()
				}
			}
			awaitSnapshot()
			assertThat(awaitSnapshot()).isEqualTo(" a [b]")
		}
	}

	@Test fun clearFocus() = runTest {
		lateinit var manager: FocusManager
		runMosaicTest {
			setContent {
				manager = LocalFocusManager.current
				Item("a")
			}
			awaitSnapshot()
			assertThat(awaitSnapshot()).isEqualTo("[a]")
			manager.clearFocus()
			assertThat(awaitSnapshot()).isEqualTo(" a")
			manager.moveFocus(FocusDirection.Next)
			assertThat(awaitSnapshot()).isEqualTo("[a]")
		}
	}

	@Test fun focusedPathGetsKeysBeforeOtherNodes() = runTest {
		val received = mutableListOf<String>()
		val requester = FocusRequester()
		runMosaicTest {
			setContent {
				var done by remember { mutableStateOf(false) }
				Column(
					modifier = Modifier
						.onPreviewKeyEvent {
							received += "root preview"
							false
						}
						.onKeyEvent {
							received += "root"
							false
						},
				) {
					Text(
						if (done) "done" else "one",
						modifier = Modifier.onKeyEvent {
							received += "one"
							done = true
							true
						},
					)
					Text(
						"two",
						modifier = Modifier
							.focusRequester(requester)
							.onKeyEvent {
								received += "two"
								false
							}
							.focusable(),
					)
				}
				LaunchedEffect(Unit) {
					requester.requestFocus()
				}
			}
			awaitSnapshot()
			sendKeyEvent(KeyboardEvent('x'.code))
			assertThat(awaitSnapshot()).isEqualTo("done\ntwo")
			// The focused path goes first, and the rest of the nodes get the unhandled key afterwards.
			assertThat(received).containsExactly("root preview", "two", "root", "one")
		}
	}

	@Test fun removedFocusMovesToTheFirstFocusable() = runTest {
		var showB by mutableStateOf(true)
		val requester = FocusRequester()
		runMosaicTest {
			setContent {
				Row {
					Item("a")
					if (showB) Item("b", modifier = Modifier.focusRequester(requester))
				}
				LaunchedEffect(Unit) {
					requester.requestFocus()
				}
			}
			awaitSnapshot()
			assertThat(awaitSnapshot()).isEqualTo(" a [b]")
			showB = false
			assertThat(awaitSnapshot()).isEqualTo(" a")
			assertThat(awaitSnapshot()).isEqualTo("[a]")
		}
	}

	@Test fun hasFocusIncludesChildren() = runTest {
		val states = mutableListOf<FocusState>()
		runMosaicTest {
			setContent {
				Column(modifier = Modifier.onFocusChanged { states += it }) {
					Item("a")
				}
			}
			awaitSnapshot()
			assertThat(states.last()).isEqualTo(FocusState(isFocused = false, hasFocus = true))
		}
	}
}
