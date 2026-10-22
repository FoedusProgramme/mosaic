package example

actual fun runInTerminal(command: String): Int {
	val shell = if (isWindows) listOf("cmd", "/c", command) else listOf("sh", "-c", command)
	return ProcessBuilder(shell).inheritIO().start().waitFor()
}

actual fun environment(name: String): String? = System.getenv(name)

actual val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
