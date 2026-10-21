package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Stable
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Color

@Stable
public fun Modifier.background(
	color: Color,
): Modifier = this.then(
	BackgroundModifier(
		color = color,
	),
)

private class BackgroundModifier(
	private val color: Color,
) : DrawModifier {
	override fun ContentDrawScope.draw() {
		drawRect(background = color)
		drawContent()
	}

	override fun toString() = "Background($color)"
}
