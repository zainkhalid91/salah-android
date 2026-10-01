package salah.core

object SalahInfo {
    const val VERSION = "1.2.0"
    const val CALCULATION_LIBRARY = "a Kotlin port of adhan-swift 1.4.0 (Batoul Apps, MIT License)"
    const val DOCUMENTATION_URL = "https://github.com/primayudantra/salah#readme"
    /** GitHub repository whose releases carry the Android APK. Override with SALAH_RELEASES_REPO. */
    val repository: String get() = System.getenv("SALAH_RELEASES_REPO")?.takeIf { it.isNotBlank() } ?: "zainkhalid91/salah-android"
    val releasesUrl: String get() = "https://github.com/$repository/releases"
}

/** A `major.minor.patch` version, parsed from strings like "1.2.3" or release tags like "v1.2.3". */
data class SemanticVersion(val major: Int, val minor: Int = 0, val patch: Int = 0) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(string: String): SemanticVersion? {
            var s = string.trim()
            if (s.startsWith("v") || s.startsWith("V")) s = s.drop(1)
            // Ignore pre-release/build suffixes ("1.2.0-beta.1", "1.2.0+5").
            val cut = s.indexOfFirst { it == '-' || it == '+' }
            if (cut >= 0) s = s.substring(0, cut)
            val parts = s.split(".").map { p -> if (p.isNotEmpty() && p.all(Char::isDigit)) p.toIntOrNull() else null }
            if (parts.size !in 1..3 || parts.any { it == null }) return null
            return SemanticVersion(parts[0]!!, parts.getOrNull(1) ?: 0, parts.getOrNull(2) ?: 0)
        }
    }
}
