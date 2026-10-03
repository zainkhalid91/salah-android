package salah.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull

sealed class ConfigException(message: String) : Exception(message) {
    /** The file exists but cannot be parsed. */
    class Corrupt(val path: String, reason: String) :
        ConfigException("The config file at $path could not be read ($reason).")
}

/**
 * Reads and writes the config JSON, in the same format as the macOS and Windows
 * apps. The Android app owns the file itself (see SalahRepository).
 */
object ConfigStore {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

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
