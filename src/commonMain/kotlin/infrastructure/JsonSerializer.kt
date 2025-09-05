package infrastructure

import common.Result
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class JsonSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    fun <T> serialize(value: T, serializer: kotlinx.serialization.KSerializer<T>): Result<String, SerializationException> {
        return try {
            Result.Success(json.encodeToString(serializer, value))
        } catch (e: SerializationException) {
            Result.Error(e)
        }
    }
    
    fun <T> deserialize(jsonString: String, deserializer: kotlinx.serialization.KSerializer<T>): Result<T, SerializationException> {
        return try {
            Result.Success(json.decodeFromString(deserializer, jsonString))
        } catch (e: SerializationException) {
            Result.Error(e)
        }
    }
}