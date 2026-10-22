package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text

class ClipTest {
	/** Draws [text] without being limited to its size of one column. */
	@Composable
	private fun Overflowing(text: String) {
		Spacer(modifier = Modifier.size(1).drawBehind { drawText(0, 0, text) })
	}

	@Test fun contentOverflowsWithoutClipping() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("123456")
					Box(modifier = Modifier.size(3, 1)) {
						Overflowing("abcdef")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("123456\nabcdef")
		}
	}

	@Test fun clipToBoundsCutsOffContent() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("123456")
					Box(modifier = Modifier.size(3, 1).clipToBounds()) {
						Overflowing("abcdef")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("123456\nabc")
		}
	}

	@Test fun clipToBoundsCutsOffWideCharacterAtTheEdge() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("123456")
					Box(modifier = Modifier.size(3, 1).clipToBounds()) {
						Overflowing("a中文")
					}
				}
			}
			// The second wide character would only half fit, so it is left out.
			assertThat(awaitSnapshot()).isEqualTo("123456\na中")
		}
	}
}
