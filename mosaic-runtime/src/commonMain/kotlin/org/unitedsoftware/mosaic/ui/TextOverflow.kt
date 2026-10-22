package org.unitedsoftware.mosaic.ui

/** How [Text] shows text which does not fit. */
public enum class TextOverflow {
	/** Cut the text off at the bounds of the node. */
	Clip,

	/** End the last visible line, and lines too wide without soft wrapping, with an ellipsis. */
	Ellipsis,
}
