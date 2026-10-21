package example

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.testing.runMosaicTest

class CounterTest {
	@Test
	fun counter() = runTest {
		runMosaicTest {
			setContent {
				Counter()
			}
			for (count in 0..20) {
				assertThat(awaitSnapshot()).isEqualTo("The count is: $count")
			}
		}
	}
}
