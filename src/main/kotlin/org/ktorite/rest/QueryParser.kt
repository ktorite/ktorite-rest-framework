package org.ktorite.rest

import io.ktor.server.application.*
import org.jetbrains.exposed.v1.core.*

internal enum class FilterOp { EQ, NE, GT, GTE, LT, LTE, CONTAINS, STARTSWITH, ENDSWITH, IN }

internal data class Filter(val field: String, val op: FilterOp, val value: String)

internal data class ParsedQuery(
    val filters: List<Filter> = emptyList(),
    val sort: List<Pair<String, Boolean>> = emptyList(),
    val fields: List<Column<*>>? = null,
    val page: Int = 1,
    val perPage: Int = 20,
    val unknownFields: List<FieldError> = emptyList()
)

internal data class FieldError(val field: String?, val message: String)

private val opPattern = Regex("^(.+?)__(eq|ne|gt|gte|lt|lte|contains|startswith|endswith|in)$")

internal fun parseQuery(call: ApplicationCall, columnMap: Map<String, Column<*>>, defaultPageSize: Int = 20, maxPageSize: Int = 100): ParsedQuery {
    val params = call.request.queryParameters

    val filters = mutableListOf<Filter>()
    val sort = mutableListOf<Pair<String, Boolean>>()
    val errors = mutableListOf<FieldError>()

    val columnNames = columnMap.keys
    val textColumnNames = columnMap.entries
        .filter { it.value.columnType is VarCharColumnType || it.value.columnType is TextColumnType }
        .map { it.key }
        .toSet()

    var page = 1
    var perPage = defaultPageSize
    var fields: List<Column<*>>? = null

    params.forEach { name, values ->
        if (values.isEmpty()) return@forEach
        val value = values.first()

        if (name == "sort") {
            value.split(",").forEach { part ->
                val trimmed = part.trim()
                if (trimmed.startsWith("-")) {
                    val field = trimmed.removePrefix("-")
                    if (field in columnNames) sort.add(field to false)
                    else errors.add(FieldError("sort:$field", "Unknown field"))
                } else if (trimmed.isNotEmpty()) {
                    if (trimmed in columnNames) sort.add(trimmed to true)
                    else errors.add(FieldError("sort:$trimmed", "Unknown field"))
                }
            }
            return@forEach
        }

        if (name == "fields") {
            if (value.isBlank()) {
                errors.add(FieldError("fields", "Empty field list"))
                return@forEach
            }
            val requested = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val invalid = requested.filter { it !in columnNames }
            invalid.forEach { errors.add(FieldError("fields:$it", "Unknown field")) }
            if (invalid.isEmpty()) {
                fields = requested.mapNotNull { columnMap[it] }
            }
            return@forEach
        }

        if (name == "page") {
            val p = value.toIntOrNull()
            if (p == null || p < 1) errors.add(FieldError("page", "Must be >= 1"))
            else page = p
            return@forEach
        }

        if (name == "per_page") {
            val p = value.toIntOrNull()
            if (p == null || p < 1) errors.add(FieldError("per_page", "Must be >= 1"))
            else perPage = p.coerceAtMost(maxPageSize)
            return@forEach
        }

        val match = opPattern.matchEntire(name)
        if (match != null) {
            val field = match.groupValues[1]
            val opStr = match.groupValues[2]
            if (field !in columnNames) { errors.add(FieldError(name, "Unknown field")); return@forEach }
            val op = when (opStr) {
                "eq" -> FilterOp.EQ
                "ne" -> FilterOp.NE
                "gt" -> FilterOp.GT
                "gte" -> FilterOp.GTE
                "lt" -> FilterOp.LT
                "lte" -> FilterOp.LTE
                "contains" -> if (field in textColumnNames) FilterOp.CONTAINS else { errors.add(FieldError(name, "Not a text column")); return@forEach }
                "startswith" -> if (field in textColumnNames) FilterOp.STARTSWITH else { errors.add(FieldError(name, "Not a text column")); return@forEach }
                "endswith" -> if (field in textColumnNames) FilterOp.ENDSWITH else { errors.add(FieldError(name, "Not a text column")); return@forEach }
                "in" -> { if (value.isBlank()) { errors.add(FieldError(name, "Empty IN values")); return@forEach }; FilterOp.IN }
                else -> return@forEach
            }
            values.forEach { v -> filters.add(Filter(field, op, v)) }
        } else {
            if (name !in columnNames) { errors.add(FieldError(name, "Unknown field")); return@forEach }
            values.forEach { v -> filters.add(Filter(name, FilterOp.EQ, v)) }
        }
    }

    return ParsedQuery(filters, sort.distinctBy { it.first }, fields, page, perPage, errors)
}