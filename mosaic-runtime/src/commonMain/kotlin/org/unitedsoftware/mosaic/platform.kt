package org.unitedsoftware.mosaic

internal expect fun env(name: String): String?

internal expect inline fun nanoTime(): Long

internal expect fun nonInteractiveExit(): Nothing
