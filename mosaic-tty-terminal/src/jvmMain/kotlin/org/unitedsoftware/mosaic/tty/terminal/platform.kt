package org.unitedsoftware.mosaic.tty.terminal

internal actual fun env(name: String): String? {
	return System.getenv(name)
}
