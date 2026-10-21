package org.unitedsoftware.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.TestChar
import org.unitedsoftware.mosaic.TestFiller
import org.unitedsoftware.mosaic.assertFailure
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.unit.IntOffset

class OffsetTest {
	@Test fun offsetHorizontalFixed() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(3, 0)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|   $TestChar
				|
				|
				|
				|
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetHorizontalFixedBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(30, 0)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetHorizontalFixedNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(-3, 0)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetVerticalFixed() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(0, 4)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				|
				|
				|
				|$TestChar
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetVerticalFixedBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(0, 40)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetVerticalFixedNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(0, -4)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetFixed() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(3, 4)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				|
				|
				|
				|   $TestChar
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetFixedBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(30, 40)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetFixedNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset(-3, -4)) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetFixedDebug() {
		val actual = Modifier.offset(3, 4).toString()
		assertThat(actual).isEqualTo("Offset(x=3, y=4)")
	}

	@Test fun offsetHorizontalModifiable() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(3, 0) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|   $TestChar
				|
				|
				|
				|
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetHorizontalModifiableBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(30, 0) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetHorizontalModifiableNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(-3, 0) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetVerticalModifiable() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(0, 4) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				|
				|
				|
				|$TestChar
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetVerticalModifiableBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(0, 40) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetVerticalModifiableNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(0, -4) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetModifiable() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(3, 4) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|
				|
				|
				|
				|   $TestChar
				|
				""".trimMargin(),
			)
		}
	}

	@Test fun offsetModifiableBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(30, 40) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetModifiableNegativeBeyondBorders() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.size(6).offset { IntOffset(-3, -4) }) {
					TestFiller(modifier = Modifier.size(1))
				}
			}
			// check when going beyond the border of the TextSurface#get
			assertFailure<IllegalStateException> {
				awaitSnapshot()
			}
		}
	}

	@Test fun offsetModifiableDebug() {
		val offsetLambda = { IntOffset(-3, -4) }
		val actual = Modifier.offset(offsetLambda).toString()
		assertThat(actual).isEqualTo("ChangeableOffset(offset=$offsetLambda)")
	}
}
