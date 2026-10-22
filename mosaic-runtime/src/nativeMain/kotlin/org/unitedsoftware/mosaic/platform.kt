package org.unitedsoftware.mosaic

import kotlin.system.exitProcess
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.toKString
import platform.posix.STDERR_FILENO
import platform.posix.getenv
import platform.posix.write

internal actual fun env(name: String): String? {
	return getenv(name)?.toKString()
}

@OptIn(UnsafeNumber::class)
internal actual fun nonInteractiveExit(): Nothing {
	val message = "$NonInteractiveMessage\n".cstr
	write(STDERR_FILENO, message, message.size.convert())
	exitProcess(1)
}

internal actual fun IntArray.rangeEquals(start: Int, other: IntArray, otherStart: Int, size: Int): Boolean {
	for (index in 0 until size) {
		if (this[start + index] != other[otherStart + index]) return false
	}
	return true
}

internal actual fun ByteArray.rangeEquals(start: Int, other: ByteArray, otherStart: Int, size: Int): Boolean {
	for (index in 0 until size) {
		if (this[start + index] != other[otherStart + index]) return false
	}
	return true
}
