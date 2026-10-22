package org.unitedsoftware.mosaic

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets other programs use the terminal for a while, like an editor or an interactive command. */
public interface TerminalControl {
	/**
	 * Gives the terminal back while [block] runs, as it was before Mosaic started, and then takes it
	 * over again and draws everything anew.
	 *
	 * Meanwhile nothing is drawn, input is left for the other programs to read, and writes to stdout
	 * and stderr are no longer intercepted. On the alternate screen, the regular screen is shown.
	 * Changes to the state are drawn once [block] returns.
	 *
	 * Concurrent calls run one after another.
	 */
	public suspend fun <T> release(block: suspend () -> T): T

	/** Whether [suspendProcess] is supported, which needs job control. It is not on Windows. */
	public val canSuspendProcess: Boolean

	/**
	 * Stops the process like Ctrl+Z normally does in a shell, with the terminal released, and returns
	 * once the shell continues it, like with `fg`. Returns false right away when [canSuspendProcess]
	 * is false.
	 *
	 * Ctrl+Z does this already when no key handler consumes it.
	 */
	public suspend fun suspendProcess(): Boolean
}

public val LocalTerminalControl: ProvidableCompositionLocal<TerminalControl> = staticCompositionLocalOf {
	throw AssertionError("No TerminalControl provided")
}
