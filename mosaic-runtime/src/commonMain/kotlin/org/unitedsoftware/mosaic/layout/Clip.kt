package org.unitedsoftware.mosaic.layout

import org.unitedsoftware.mosaic.modifier.Modifier

/**
 * Clips the content of this node, including its children and anything drawn by later modifiers,
 * to its bounds. Without it, content which does not fit, such as a child placed partly outside,
 * is drawn over its surroundings.
 */
public fun Modifier.clipToBounds(): Modifier = this then ClipToBoundsModifier

internal object ClipToBoundsModifier : DrawModifier {
	override fun ContentDrawScope.draw() {
		(this as TextCanvasDrawScope).clipToBounds {
			drawContent()
		}
	}

	override fun toString() = "ClipToBounds"
}
