package org.unitedsoftware.mosaic

import kotlin.system.exitProcess

internal actual fun env(name: String): String? {
	return System.getenv(name)
}

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun nanoTime(): Long = System.nanoTime()

internal actual fun nonInteractiveExit(): Nothing {
	System.err.apply {
		println(NonInteractiveMessage)
		flush()
	}
	exitProcess(1)
}

// The JIT compiles these to vectorized comparisons.
internal actual fun IntArray.rangeEquals(start: Int, other: IntArray, otherStart: Int, size: Int): Boolean = java.util.Arrays.equals(this, start, start + size, other, otherStart, otherStart + size)

internal actual fun ByteArray.rangeEquals(start: Int, other: ByteArray, otherStart: Int, size: Int): Boolean = java.util.Arrays.equals(this, start, start + size, other, otherStart, otherStart + size)
