package org.ktorite.rest

import kotlinx.serialization.json.*
import java.util.UUID

/**
 *  Converts a DB column value to/from JSON and URL strings.
 */
interface ColumnSerializer {
    fun toJson(value: Any?): JsonElement
    fun fromJson(element: JsonElement): Any?
    fun fromString(raw: String): Any?
}

object IntSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as Int)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.intOrNull
    override fun fromString(raw: String): Any? = raw.toIntOrNull()
}

object LongSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as Long)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.longOrNull
    override fun fromString(raw: String): Any? = raw.toLongOrNull()
}

object ShortSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as Short)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.content.toShortOrNull()
    override fun fromString(raw: String): Any? = raw.toShortOrNull()
}

object StringSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as String)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.contentOrNull
    override fun fromString(raw: String): Any? = raw
}

object BooleanSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as Boolean)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.booleanOrNull
    override fun fromString(raw: String): Any? = raw.toBooleanStrictOrNull()
}

object DoubleSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement {
        if (value == null) return JsonNull
        val d = when (value) {
            is Double -> value
            is java.math.BigDecimal -> value.toDouble()
            is Number -> value.toDouble()
            else -> return JsonNull
        }
        return JsonPrimitive(d)
    }
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.content.toDoubleOrNull()
    override fun fromString(raw: String): Any? = raw.toDoubleOrNull()
}

object FloatSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value as Float)
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.content.toFloatOrNull()
    override fun fromString(raw: String): Any? = raw.toFloatOrNull()
}

object BigDecimalSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement {
        if (value == null) return JsonNull
        return JsonPrimitive((value as java.math.BigDecimal).toPlainString())
    }
    override fun fromJson(element: JsonElement): Any? = element.jsonPrimitive.content.toBigDecimalOrNull()
    override fun fromString(raw: String): Any? = raw.toBigDecimalOrNull()
}

object UuidSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value.toString())
    override fun fromJson(element: JsonElement): Any? {
        val s = element.jsonPrimitive.contentOrNull ?: return null
        return try { UUID.fromString(s) } catch (_: Exception) { null }
    }
    override fun fromString(raw: String): Any? = try { UUID.fromString(raw) } catch (_: Exception) { null }
}

object InstantSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value.toString())
    override fun fromJson(element: JsonElement): Any? {
        val s = element.jsonPrimitive.contentOrNull ?: return null
        return try { kotlin.time.Instant.parse(s) } catch (_: Exception) { null }
    }
    override fun fromString(raw: String): Any? = try { kotlin.time.Instant.parse(raw) } catch (_: Exception) { null }
}

internal val localDateClass by lazy { runCatching { Class.forName("kotlinx.datetime.LocalDate") }.getOrNull() }
internal val localDateTimeClass by lazy { runCatching { Class.forName("kotlinx.datetime.LocalDateTime") }.getOrNull() }

private fun reflectParse(cls: Class<*>?, raw: String): Any? = try {
    cls?.getMethod("parse", String::class.java)?.invoke(null, raw)
} catch (_: Exception) { null }

object LocalDateSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value.toString())
    override fun fromJson(element: JsonElement): Any? {
        val s = element.jsonPrimitive.contentOrNull ?: return null
        return reflectParse(localDateClass, s)
    }
    override fun fromString(raw: String): Any? = reflectParse(localDateClass, raw)
}

object LocalDateTimeSerializer : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value.toString())
    override fun fromJson(element: JsonElement): Any? {
        val s = element.jsonPrimitive.contentOrNull ?: return null
        return reflectParse(localDateTimeClass, s)
    }
    override fun fromString(raw: String): Any? = reflectParse(localDateTimeClass, raw)
}

class EnumSerializer(private val enumClass: Class<*>) : ColumnSerializer {
    override fun toJson(value: Any?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive((value as Enum<*>).name)
    override fun fromJson(element: JsonElement): Any? {
        val s = element.jsonPrimitive.contentOrNull ?: return null
        return reflectValueOf(enumClass, s)
    }
    override fun fromString(raw: String): Any? = reflectValueOf(enumClass, raw)
}

private fun reflectValueOf(enumClass: Class<*>, raw: String): Any? = try {
    enumClass.getMethod("valueOf", String::class.java).invoke(null, raw)
} catch (_: Exception) { null }
