package salah.core

import java.nio.file.Paths

/** OS detection and per-user locations shared by the app and the CLI. */
object Platform {
    private val os = System.getProperty("os.name").lowercase()
    val isWindows = os.startsWith("windows")
    val isMac = os.startsWith("mac")

    /** Where config.json and the app's runtime files live. */
    fun configDirectory(): String {
        val home = System.getProperty("user.home")
        return when {
            isWindows -> Paths.get(System.getenv("APPDATA") ?: Paths.get(home, "AppData", "Roaming").toString(), "Salah").toString()
            isMac -> Paths.get(home, "Library", "Application Support", "Salah").toString()
            else -> Paths.get(System.getenv("XDG_CONFIG_HOME") ?: Paths.get(home, ".config").toString(), "salah").toString()
        }
    }
}
