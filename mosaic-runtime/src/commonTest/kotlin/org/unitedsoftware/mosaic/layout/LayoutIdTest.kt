package org.unitedsoftware.mosaic.layout

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AtLeastSize
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Layout

class LayoutIdTest {

	@Test fun testTags() = runTest {
		runMosaicTest {
			setContent {
				Layout({
					AtLeastSize(0, Modifier.layoutId("first"), content = {})
					Box(Modifier.layoutId("second")) { AtLeastSize(0, content = {}) }
					Box(Modifier.layoutId("third")) { AtLeastSize(0, content = {}) }
				}) { measurables, _ ->
					assertThat(measurables).hasSize(3)
					assertThat(measurables[0].layoutId).isEqualTo("first")
					assertThat(measurables[1].layoutId).isEqualTo("second")
					assertThat(measurables[2].layoutId).isEqualTo("third")
					layout(0, 0) {}
				}
			}
			awaitSnapshot()
		}
	}
}
