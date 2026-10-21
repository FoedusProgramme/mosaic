package org.unitedsoftware.mosaic.tty.terminal

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import org.unitedsoftware.mosaic.terminal.DebugEvent
import org.unitedsoftware.mosaic.terminal.Event
import org.unitedsoftware.mosaic.terminal.FocusEvent
import org.unitedsoftware.mosaic.terminal.ResizeEvent
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.tty.Tty

internal class EventParserTtyCallback(
	private val focused: MutableStateFlow<Boolean>,
	private val size: MutableStateFlow<Terminal.Size>,
	private val events: SendChannel<Event>,
	private val emitDebugEvents: Boolean,
) : Tty.Callback {
	override fun onFocus(focused: Boolean) {
		this.focused.value = focused
		sendEvent(FocusEvent(focused))
	}

	override fun onKey() {
		return
	}

	override fun onMouse() {
		return
	}

	override fun onResize(columns: Int, rows: Int, width: Int, height: Int) {
		size.value = Terminal.Size(columns, rows, width, height)
		sendEvent(ResizeEvent(columns, rows, width, height))
	}

	private fun sendEvent(event: Event) {
		events.trySend(event)
		if (emitDebugEvents) {
			events.trySend(DebugEvent(event, byteArrayOf()))
		}
	}
}
