package org.unitedsoftware.mosaic.layout

import org.unitedsoftware.mosaic.layout.Placeable.PlacementScope

public interface MeasureScope {
	public fun layout(
		width: Int,
		height: Int,
		placementBlock: PlacementScope.() -> Unit,
	): MeasureResult {
		return LayoutResult(this as PlacementScope, width, height, placementBlock)
	}

	private class LayoutResult(
		private val placementScope: PlacementScope,
		override val width: Int,
		override val height: Int,
		private val placementBlock: PlacementScope.() -> Unit,
	) : MeasureResult {
		override fun placeChildren() = placementScope.placementBlock()
	}
}
