package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Stable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier

/**
 * Applies a tag to allow modified element to be found in tests.
 */
@Stable
internal fun Modifier.testTag(tag: String): Modifier = this then TestTagModifier(tag)

@Poko
internal class TestTagModifier(internal val tag: String) : Modifier.Element {

	override fun toString(): String = "TestTag($tag)"
}
