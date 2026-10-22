package org.unitedsoftware.mosaic

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class LineBufferTest {
	@Test fun splitsLinesAndKeepsPartialOnes() {
		val lines = mutableListOf<String>()
		val buffer = LineBuffer()
		buffer.append("one\r\ntwo\nthr".encodeToByteArray(), 12, lines::add)
		assertThat(lines).containsExactly("one", "two")
		assertThat(buffer.remaining()).isEqualTo("thr")

		buffer.append("ee\n".encodeToByteArray(), 3, lines::add)
		assertThat(lines).containsExactly("one", "two", "three")
		assertThat(buffer.remaining()).isNull()
	}

	@Test fun codePointsSplitAcrossReadsAreKept() {
		val lines = mutableListOf<String>()
		val buffer = LineBuffer()
		val bytes = "中文\n".encodeToByteArray()
		buffer.append(bytes.copyOfRange(0, 2), 2, lines::add)
		buffer.append(bytes.copyOfRange(2, bytes.size), bytes.size - 2, lines::add)
		assertThat(lines).containsExactly("中文")
	}

	@Test fun onlyCountBytesAreUsed() {
		val lines = mutableListOf<String>()
		val buffer = LineBuffer()
		buffer.append("a\nb\n".encodeToByteArray(), 2, lines::add)
		assertThat(lines).containsExactly("a")
		assertThat(buffer.remaining()).isNull()
	}
}
