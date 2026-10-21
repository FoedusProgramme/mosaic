package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.TertiaryDeviceAttributesEvent
import org.unitedsoftware.mosaic.terminal.UnknownEvent

class EventParserDcsTertiaryDeviceAttributesEventTest : BaseEventParserTest() {
	@Test fun zeroes() {
		testTerminal.write("$DCS!|00000000$ST")
		assertThat(parser.next()).isEqualTo(TertiaryDeviceAttributesEvent(0, 0))
	}

	@Test fun values() {
		testTerminal.write("$DCS!|7B06F855$ST")
		assertThat(parser.next()).isEqualTo(TertiaryDeviceAttributesEvent(123, 456789))
	}

	@Test fun tooShort() {
		testTerminal.write("$DCS!|0000000$ST")
		assertThat(parser.next()).isEqualTo(
			UnknownEvent("1b50217c303030303030301b5c".hexToByteArray()),
		)
	}

	@Test fun tooLong() {
		testTerminal.write("$DCS!|000000000$ST")
		assertThat(parser.next()).isEqualTo(
			UnknownEvent("1b50217c3030303030303030301b5c".hexToByteArray()),
		)
	}

	@Test fun idOddHex() {
		testTerminal.write("$DCS!|7B06F85$ST")
		assertThat(parser.next()).isEqualTo(
			UnknownEvent("1b50217c374230364638351b5c".hexToByteArray()),
		)
	}
}
