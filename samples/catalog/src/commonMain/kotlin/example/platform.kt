package example

/** Runs [command] with the shell, attached to the terminal, and returns its exit code. */
expect fun runInTerminal(command: String): Int

expect fun environment(name: String): String?

expect val isWindows: Boolean
