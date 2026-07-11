package org.ktorite.rest

import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.*

@Suppress("UNCHECKED_CAST")
internal fun setCol(row: UpdateBuilder<*>, col: Column<*>, value: Any?) {
    row[col as Column<Any?>] = value
}

internal fun serializeRow(table: Table, row: ResultRow, columns: List<Column<*>> = table.columns, customSerializers: Map<Class<*>, ColumnSerializer> = emptyMap()): JsonObject {
    return buildJsonObject {
        columns.forEach { col ->
            val value = row[col]
            put(col.name, serializerFor(col, customSerializers).toJson(value))
        }
    }
}

internal fun serializeList(table: Table, rows: List<ResultRow>, columns: List<Column<*>> = table.columns, customSerializers: Map<Class<*>, ColumnSerializer> = emptyMap()): JsonArray {
    return JsonArray(rows.map { serializeRow(table, it, columns, customSerializers) })
}
