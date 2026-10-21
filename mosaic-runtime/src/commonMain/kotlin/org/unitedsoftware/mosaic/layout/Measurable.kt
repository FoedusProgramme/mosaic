package org.unitedsoftware.mosaic.layout

import org.unitedsoftware.mosaic.ui.unit.Constraints

public interface Measurable : IntrinsicMeasurable {
	public fun measure(constraints: Constraints): Placeable
}
