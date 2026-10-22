package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Stable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntOffset

/**
 * Offset the content by ([x], [y]). The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * A positive [x] offset will always move the content to the right.
 */
@Stable
public fun Modifier.offset(x: Int = 0, y: Int = 0): Modifier = this then
	OffsetModifier(x = x, y = y)

/**
 * Offset the content by [offset]. The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * This modifier is designed to be used for offsets that change, possibly due to user interactions.
 * It avoids recomposition when the offset is changing, and also adds a graphics layer that
 * prevents unnecessary redrawing of the context when the offset is changing.
 *
 * A positive horizontal offset will always move the content to the right.
 */
public fun Modifier.offset(offset: () -> IntOffset): Modifier = this then
	ChangeableOffsetModifier(offset = offset)

@Poko
private class OffsetModifier(
	private val x: Int,
	private val y: Int,
) : LayoutModifier {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val placeable = measurable.measure(constraints)
		return layout(placeable.width, placeable.height) {
			placeable.place(this@OffsetModifier.x, this@OffsetModifier.y)
		}
	}

	override fun toString(): String = "Offset(x=$x, y=$y)"
}

@Poko
private class ChangeableOffsetModifier(
	private val offset: () -> IntOffset,
) : LayoutModifier {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val placeable = measurable.measure(constraints)
		return layout(placeable.width, placeable.height) {
			val offsetValue = offset()
			placeable.place(offsetValue.x, offsetValue.y)
		}
	}

	override fun toString(): String = "ChangeableOffset(offset=$offset)"
}
