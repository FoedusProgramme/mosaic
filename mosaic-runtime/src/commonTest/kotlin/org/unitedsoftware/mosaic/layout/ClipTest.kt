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

class ClipTest {
	@Test fun contentOverflowsWithoutClipping() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("123456")
					Box(modifier = Modifier.size(3, 1)) {
						Text("abcdef")
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
						Text("abcdef")
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
						Text("a中文")
					}
				}
			}
			// The second wide character would only half fit, so it is left out.
			assertThat(awaitSnapshot()).isEqualTo("123456\na中")
		}
	}
}
