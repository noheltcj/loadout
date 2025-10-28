package data.serialization

import domain.entity.packaging.Result
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class JsonSerializer {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    fun <T> serialize(
        value: T,
        serializer: KSerializer<T>,
    ): Result<String, SerializationException> =
        try {
            Result.Success(json.encodeToString(serializer, value))
        } catch (e: SerializationException) {
            Result.Error(e)
        }

    fun <T> deserialize(
        jsonString: String,
        deserializer: KSerializer<T>,
    ): Result<T, SerializationException> =
        try {
            Result.Success(json.decodeFromString(deserializer, jsonString))
        } catch (e: SerializationException) {
            Result.Error(e)
        }
}
