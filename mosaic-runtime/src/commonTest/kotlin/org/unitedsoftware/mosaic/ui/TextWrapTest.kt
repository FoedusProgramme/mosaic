package org.unitedsoftware.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.IntrinsicSize
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle

class TextWrapTest {
	@Test fun wrapsToTheMaximumWidth() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(9)) {
					Text("hello big world")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("hello big\nworld")
		}
	}

	@Test fun wrapsAnnotatedStrings() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(5)) {
					Text(
						buildAnnotatedString {
							append("ab ")
							withStyle(SpanStyle(textStyle = TextStyle.Bold)) { append("cd ef") }
						},
					)
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("ab cd\nef")
		}
	}

	@Test fun withoutSoftWrapLinesAreClipped() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Box(modifier = Modifier.width(5)) {
						Text("hello world", softWrap = false)
					}
					Text("|")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("hello\n|")
		}
	}

	@Test fun maxLines() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("1\n2\n3", maxLines = 2)
					Text("|")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("1\n2\n|")
		}
	}

	@Test fun ellipsisAfterTheLastLine() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(9)) {
					Text("hello big wide world", maxLines = 2, overflow = TextOverflow.Ellipsis)
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("hello big\nwide…")
		}
	}

	@Test fun ellipsisCutsTheLastLineToFit() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(5)) {
					Text("abcdefgh\nij", maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("abcd…")
		}
	}

	@Test fun ellipsisWithoutSoftWrap() = runTest {
		runMosaicTest {
			setContent {
				Box(modifier = Modifier.width(6)) {
					Text("中文中文", softWrap = false, overflow = TextOverflow.Ellipsis)
				}
			}
			// The second wide character does not fit before the ellipsis.
			assertThat(awaitSnapshot()).isEqualTo("中文…")
		}
	}

	@Test fun intrinsicWidths() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Box(modifier = Modifier.width(IntrinsicSize.Min)) {
						Text("one three")
					}
					Box(modifier = Modifier.width(IntrinsicSize.Max)) {
						Text("one three")
					}
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("one\nthree\none three")
		}
	}
}
