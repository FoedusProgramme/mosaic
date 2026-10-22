package org.unitedsoftware.mosaic

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.unitedsoftware.mosaic.tty.StandardStreams
import org.unitedsoftware.mosaic.tty.StandardStreams.InterceptedStreams

/** Where the frames are written, and what becomes of other writes to stdout and stderr. */
internal interface Output {
	fun write(text: String)

	/**
	 * Starts passing other writes to [onLine], one line at a time and on another thread, until
	 * [stop] is called.
	 */
	fun startIntercepting(onLine: (String) -> Unit) {}

	/** Stops intercepting, and writes what was left of the intercepted output. */
	suspend fun stop() {}

	/** Stops intercepting while other programs use the terminal, until [resume]. */
	suspend fun pause() {}

	fun resume() {}
}

internal object PrintOutput : Output {
	override fun write(text: String) {
		print(text)
	}
}

/**
 * Writes frames to the real stdout while writes through other means, like [println] or a logging
 * library, are intercepted. Otherwise they would end up in the middle of the frames and corrupt
 * the display.
 */
internal class InterceptingOutput private constructor(
	private val streams: StandardStreams,
	private var intercepted: InterceptedStreams?,
) : Output {
	private var onLine: (String) -> Unit = {}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	/** Each reader returns the output which was left once [stop] interrupted it. */
	private val readers = ArrayList<Deferred<String>>()

	@Volatile
	private var stopping = false

	override fun write(text: String) {
		writeAll(text.encodeToByteArray())
	}

	private fun writeAll(bytes: ByteArray) {
		var offset = 0
		while (offset < bytes.size) {
			val written = streams.writeOutput(bytes, offset, bytes.size - offset)
			if (written <= 0) break
			offset += written
		}
	}

	override fun startIntercepting(onLine: (String) -> Unit) {
		this.onLine = onLine
		val intercepted = intercepted ?: return
		stopping = false
		readers += scope.async {
			readLines(intercepted::readOutput, intercepted::readOutputWithTimeout, onLine)
		}
		readers += scope.async {
			readLines(intercepted::readError, intercepted::readErrorWithTimeout, onLine)
		}
	}

	private fun readLines(
		read: (ByteArray, Int, Int) -> Int,
		readWithTimeout: (ByteArray, Int, Int, Int) -> Int,
		onLine: (String) -> Unit,
	): String {
		val lines = LineBuffer()
		val buffer = ByteArray(8192)
		val leftovers = StringBuilder()
		try {
			while (!stopping) {
				val count = read(buffer, 0, buffer.size)
				lines.append(buffer, count, onLine)
			}
			// Drain what was written before the interruption, which closing would otherwise discard.
			while (true) {
				val count = readWithTimeout(buffer, 0, buffer.size, 0)
				if (count <= 0) break
				lines.append(buffer, count) { leftovers.append(it).append('\n') }
			}
		} catch (_: Exception) {
			// Reading failed, so there is nothing more to read.
		}
		lines.remaining()?.let(leftovers::append)
		return leftovers.toString()
	}

	override suspend fun stop() {
		try {
			pause()
		} finally {
			streams.close()
		}
	}

	override suspend fun pause() {
		val intercepted = intercepted ?: return
		this.intercepted = null
		stopping = true
		intercepted.interruptOutputRead()
		intercepted.interruptErrorRead()
		val leftovers = try {
			readers.awaitAll()
		} finally {
			readers.clear()
			intercepted.close()
		}

		// The display is done, so the rest can go below it. The terminal is still in raw mode.
		for (rest in leftovers) {
			if (rest.isNotEmpty()) {
				print(rest.replace("\n", "\r\n"))
			}
		}
	}

	override fun resume() {
		intercepted = try {
			streams.interceptOtherWrites()
		} catch (_: Exception) {
			null
		}
		startIntercepting(onLine)
	}

	companion object {
		/** Returns null when the streams cannot be intercepted, like when they already are. */
		fun tryStart(): InterceptingOutput? {
			val streams = try {
				StandardStreams.bind()
			} catch (_: Exception) {
				return null
			}
			if (!streams.isOutputTty()) {
				streams.close()
				return null
			}
			val intercepted = try {
				streams.interceptOtherWrites()
			} catch (_: Exception) {
				streams.close()
				return null
			}
			return InterceptingOutput(streams, intercepted)
		}
	}
}

/** Splits bytes into lines, keeping bytes of partial lines and code points until they complete. */
internal class LineBuffer {
	private var bytes = ByteArray(256)
	private var size = 0

	fun append(buffer: ByteArray, count: Int, onLine: (String) -> Unit) {
		for (index in 0 until count) {
			val byte = buffer[index]
			if (byte == '\n'.code.toByte()) {
				var end = size
				if (end > 0 && bytes[end - 1] == '\r'.code.toByte()) end--
				onLine(bytes.decodeToString(0, end))
				size = 0
			} else {
				if (size == bytes.size) bytes = bytes.copyOf(size * 2)
				bytes[size++] = byte
			}
		}
	}

	/** The partial line, if any. */
	fun remaining(): String? = if (size == 0) null else bytes.decodeToString(0, size)
}
