package org.unitedsoftware.mosaic

internal expect fun env(name: String): String?

internal expect inline fun nanoTime(): Long

internal expect fun nonInteractiveExit(): Nothing

/** Whether [size] elements of this array from [start] equal those of [other] from [otherStart]. */
internal expect fun IntArray.rangeEquals(start: Int, other: IntArray, otherStart: Int, size: Int): Boolean

/** Whether [size] elements of this array from [start] equal those of [other] from [otherStart]. */
internal expect fun ByteArray.rangeEquals(start: Int, other: ByteArray, otherStart: Int, size: Int): Boolean
