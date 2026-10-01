package salah.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

sealed class ConfigException(message: String) : Exception(message) {
    /** The file exists but cannot be parsed. */
    class Corrupt(val path: String, reason: String) :
        ConfigException("The config file at $path could not be read ($reason).")

    class WriteFailed(val path: String, reason: String) :
        ConfigException("Could not save the config file at $path ($reason).")
}

/**
 * Reads and atomically writes the shared config:
 * `%APPDATA%\Salah\config.json` on Windows, the same path as the macOS app on macOS,
 * and `$XDG_CONFIG_HOME/salah/config.json` elsewhere.
 *
 * Set `SALAH_CONFIG_PATH` to point both app and CLI at another file (used by tests).
 */
class ConfigStore(val path: Path = defaultPath()) {
    val exists: Boolean get() = Files.isRegularFile(path)

    /** Returns defaults when no file exists yet; throws [ConfigException.Corrupt] for unreadable files. */
    fun load(): SalahConfig {
        if (!exists) return SalahConfig.DEFAULT
        val text = try {
            Files.readString(path)
        } catch (e: IOException) {
            throw ConfigException.Corrupt(path.toString(), e.message ?: e.javaClass.simpleName)
        }
        return decode(text, path.toString())
    }

    /**
     * Writes to a temporary file in the same directory, then renames it over the target,
     * so a concurrent reader sees either the old or the new file, never a partial one.
     */
    fun save(config: SalahConfig) {
        val dir = path.toAbsolutePath().parent
        val tmp = dir.resolve(".config-${UUID.randomUUID()}.tmp")
        try {
            Files.createDirectories(dir)
            Files.writeString(tmp, encode(config))
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(tmp) }
            throw ConfigException.WriteFailed(path.toString(), e.message ?: e.javaClass.simpleName)
        }
    }

    /** Load, mutate, save. Used by the CLI for single-key edits. */
    fun update(body: (SalahConfig) -> SalahConfig): SalahConfig {
        val c = body(load())
        save(c)
        return c
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            encodeDefaults = true
            isLenient = false
        }
        private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

        fun defaultPath(): Path {
            System.getenv("SALAH_CONFIG_PATH")?.takeIf { it.isNotBlank() }?.let { o ->
                val expanded = if (o.startsWith("~")) System.getProperty("user.home") + o.substring(1) else o
                return Paths.get(expanded)
            }
            return Paths.get(Platform.configDirectory(), "config.json")
        }

        fun decode(text: String, path: String = "config.json"): SalahConfig {
            val element = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ConfigException.Corrupt(path, "not valid JSON")
            }
            val obj = element as? JsonObject ?: throw ConfigException.Corrupt(path, "top level is not an object")
            val migrated = ConfigMigrator.migrate(obj)
            return try {
                json.decodeFromJsonElement<SalahConfig>(migrated).normalized()
            } catch (e: Exception) {
                val reason = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "wrong type"
                throw ConfigException.Corrupt(path, reason)
            }
        }

        /** Pretty-printed with sorted keys, like the macOS app's JSONEncoder. */
        fun encode(config: SalahConfig): String {
            val el = json.encodeToJsonElement(config.copy(schemaVersion = SalahConfig.CURRENT_SCHEMA_VERSION))
            return pretty.encodeToString(JsonElement.serializer(), sortKeys(el)).replace("\" : ", "\": ")
        }

        private fun sortKeys(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to sortKeys(it.value) })
            is JsonArray -> JsonArray(e.map { sortKeys(it) })
            else -> e
        }
    }
}

/** Forward-only schema migration on raw JSON. Unknown keys pass through untouched. */
object ConfigMigrator {
    fun migrate(json: JsonObject): JsonObject {
        val version = (json["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        // Future migrations go here, e.g. `if (version < 2) json = migrate1to2(json)`.
        if (version < SalahConfig.CURRENT_SCHEMA_VERSION) {
            return JsonObject(json + ("schemaVersion" to JsonPrimitive(SalahConfig.CURRENT_SCHEMA_VERSION)))
        }
        return json
    }
}
