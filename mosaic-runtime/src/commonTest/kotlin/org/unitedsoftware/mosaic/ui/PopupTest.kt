package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.clipToBounds
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.size
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.unit.IntOffset

class PopupTest {
	@Test fun takesNoSpaceAndDrawsOverContent() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("aaaa")
					Popup(offset = IntOffset(1, 0)) {
						Text("XY")
					}
					Text("bbbb")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("aaaa\nbXYb")
		}
	}

	@Test fun clearsTheAreaBelowIt() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("aaaa", modifier = Modifier.background(Color.Red))
					Popup {
						Text("X", modifier = Modifier.size(3, 1))
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("aaaa\nX")
		}
	}

	@Test fun isNotClippedByItsAncestors() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("0123456789")
					Box(modifier = Modifier.size(2, 1).clipToBounds()) {
						Popup {
							Text("wide text")
						}
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("0123456789\nwide text")
		}
	}

	@Test fun growsTheOutputBelowTheContent() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("a")
					Popup {
						Text("1\n2")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("a\n1\n2")
		}
	}

	@Test fun bottomAlignmentExtendsUpwards() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("x")
					Text("y")
					Text("z")
					Popup(alignment = Alignment.BottomStart) {
						Text("P\nQ")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("x\nP\nQ")
		}
	}

	@Test fun staysWithinTheWidth() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("abcd")
					Popup(offset = IntOffset(3, 0)) {
						Text("XY")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("abcd\n  XY")
		}
	}

	@Test fun receivesKeysFirstAndCanBeRemoved() = runTest {
		runMosaicTest {
			setContent {
				var open by remember { mutableStateOf(true) }
				var keys by remember { mutableStateOf("") }
				Column(
					modifier = Modifier.onKeyEvent {
						keys += it.key
						true
					},
				) {
					Text("keys:$keys")
					if (open) {
						Popup {
							Text(
								"menu",
								modifier = Modifier.onKeyEvent {
									if (it.key != "Escape") return@onKeyEvent false
									open = false
									true
								},
							)
						}
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("keys:\nmenu")
			sendKeyEvent(KeyboardEvent(27))
			assertThat(awaitSnapshot()).isEqualTo("keys:")
			sendKeyEvent(KeyboardEvent(27))
			assertThat(awaitSnapshot()).isEqualTo("keys:Escape")
		}
	}
}
