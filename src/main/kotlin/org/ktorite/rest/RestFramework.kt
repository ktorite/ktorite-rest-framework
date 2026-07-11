package org.ktorite.rest

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.ktorite.config.KtoriteConfig

fun KtoriteConfig.restFramework(block: RestConfig.() -> Unit) {
    val restConfig = RestConfig(registeredModels).apply(block)
    val registrations = restConfig.registrations
        .groupBy { it.table.tableName }
        .map { it.value.last() }
    if (registrations.isEmpty()) return
    val customSerializers = restConfig.serializers
    fun <T> dbQuery(block: Transaction.() -> T): T =
        transaction(this@restFramework.db ?: error("No database configured")) { block() }

    suspend fun ApplicationCall.restRespond(block: suspend ApplicationCall.() -> Unit) {
        try { block() } catch (e: Exception) {
            application.log.error("Unhandled exception in REST handler", e)
            respondError("Internal server error", HttpStatusCode.InternalServerError)
        }
    }

    suspend fun ApplicationCall.parsePkValues(pkCols: List<Column<*>>): List<Any?>? {
        val idStr = parameters["id"] ?: return respondError("Missing id", HttpStatusCode.BadRequest).let { null }
        val pkValues = mutableListOf<Any?>()
        val parts = idStr.split(",").map { it.trim() }
        pkCols.forEachIndexed { i, col ->
            val s = parts.getOrNull(i) ?: return respondError("Invalid id", HttpStatusCode.BadRequest).let { null }
            val value = serializerFor(col, customSerializers).fromString(s) ?: return respondError(
                "Invalid id",
                HttpStatusCode.BadRequest
            ).let { null }
            pkValues.add(value)
        }
        return pkValues
    }

    data class BodyResult(val errors: List<FieldError>, val values: List<Pair<Column<*>, Any?>>)

    fun parseBody(body: JsonObject, columnMap: Map<String, Column<*>>, skipPk: (Column<*>) -> Boolean): BodyResult {
        val errors = mutableListOf<FieldError>()
        val values = mutableListOf<Pair<Column<*>, Any?>>()
        body.forEach { (key, value) ->
            val col = columnMap[key]
            if (col == null) {
                errors.add(FieldError(key, "Unknown field"))
                return@forEach
            }
            if (skipPk(col)) return@forEach
            if (value is JsonNull) { errors.add(FieldError(key, "Null is not allowed")); return@forEach }
            val converted = serializerFor(col, customSerializers).fromJson(value)
            if (converted == null) {
                errors.add(FieldError(key, "Invalid value"))
            } else {
                values.add(col to converted)
            }
        }
        return BodyResult(errors, values)
    }

    routing {
        registrations.forEach { reg ->
            val table = reg.table
            val basePath = "/api/${reg.path}"
            val pkCols = pkColumns(table)
            val columnMap = table.columns.associateBy { it.name }

            get("$basePath/") {
                call.restRespond {
                    val pq = parseQuery(call, columnMap, reg.pageSize, reg.maxPageSize)
                    if (pq.unknownFields.isNotEmpty()) {
                        call.respondFieldErrors(pq.unknownFields, HttpStatusCode.BadRequest)
                        return@restRespond
                    }
                    val columns = pq.fields ?: table.columns
                    if (reg.paginated) {
                        val (rows, total) = dbQuery {
                            val total = table.selectAll()
                                .withFilters(pq.filters, columnMap, customSerializers)
                                .count()
                            val rows = table.selectAll()
                                .withFilters(pq.filters, columnMap, customSerializers)
                                .withSort(pq.sort, columnMap)
                                .limit(pq.perPage)
                                .offset(((pq.page - 1).toLong()) * pq.perPage)
                                .toList()
                            rows to total
                        }
                        val result = buildJsonObject {
                            put("count", JsonPrimitive(total))
                            put("page", JsonPrimitive(pq.page))
                            put("per_page", JsonPrimitive(pq.perPage))
                            put("results", serializeList(table, rows, columns, customSerializers))
                        }
                        call.respondText(Json.encodeToString(result), ContentType.Application.Json)
                    } else {
                        val rows = dbQuery {
                            table.selectAll()
                                .withFilters(pq.filters, columnMap, customSerializers)
                                .withSort(pq.sort, columnMap)
                                .toList()
                        }
                        val result = serializeList(table, rows, columns, customSerializers)
                        call.respondText(Json.encodeToString(result), ContentType.Application.Json)
                    }
                }
            }
            get(basePath) {
                call.respondRedirect("$basePath/")
            }

            get("$basePath/{id}/") {
                call.respondRedirect("../${call.parameters["id"]}")
            }

            get("$basePath/{id}") {
                call.restRespond {
                    val pkValues = call.parsePkValues(pkCols) ?: return@restRespond
                    val row = dbQuery {
                        table.selectAll().where { pkCols eqValues pkValues }.singleOrNull()
                    }
                    if (row == null) {
                        call.respondError("Not found", HttpStatusCode.NotFound)
                    } else {
                        val result = serializeRow(table, row, table.columns, customSerializers)
                        call.respondText(Json.encodeToString(result), ContentType.Application.Json)
                    }
                }
            }

            if (!reg.readOnly) {
                post("$basePath/") {
                    call.restRespond {
                        val body = call.receive<JsonObject>()
                        val (errors, values) = parseBody(body, columnMap) {
                            it in pkCols && it.columnType is AutoIncColumnType<*>
                        }
                        if (errors.isNotEmpty()) {
                            call.respondFieldErrors(errors, HttpStatusCode.BadRequest)
                            return@restRespond
                        }
                        if (values.isEmpty()) {
                            call.respondFieldErrors(listOf(FieldError(null, "No fields supplied")), HttpStatusCode.BadRequest)
                            return@restRespond
                        }
                        val insertResult = dbQuery {
                            table.insert { row -> values.forEach { (col, v) -> setCol(row, col, v) } }
                        }
                        val ids = pkCols.map { serializerFor(it, customSerializers).toJson(insertResult.get(it)) }
                        val result = buildJsonObject {
                            if (ids.size == 1) put("id", ids[0])
                            else put("id", JsonArray(ids))
                            put("status", JsonPrimitive("created"))
                        }
                        call.respondText(Json.encodeToString(result), ContentType.Application.Json, HttpStatusCode.Created)
                    }
                }
                post(basePath) {
                    call.response.header(HttpHeaders.Location, "$basePath/")
                    call.respondText("", status = HttpStatusCode.TemporaryRedirect)
                }

                suspend fun ApplicationCall.updateEntity(pkValues: List<Any?>) {
                    val body = receive<JsonObject>()
                    val (errors, values) = parseBody(body, columnMap) { it in pkCols }
                    if (errors.isNotEmpty()) {
                        respondFieldErrors(errors, HttpStatusCode.BadRequest)
                        return
                    }
                    if (values.isEmpty()) {
                        respondFieldErrors(listOf(FieldError(null, "No fields supplied")), HttpStatusCode.BadRequest)
                        return
                    }
                    val updated = dbQuery {
                        table.update(where = { pkCols eqValues pkValues }, limit = null) { row ->
                            values.forEach { (col, v) -> setCol(row, col, v) }
                        }
                    }
                    if (updated == 0) {
                        respondError("Not found", HttpStatusCode.NotFound)
                    } else {
                        val result = buildJsonObject { put("status", JsonPrimitive("updated")) }
                        respondText(Json.encodeToString(result), ContentType.Application.Json)
                    }
                }

                put("$basePath/{id}/") {
                    call.respondRedirect("../${call.parameters["id"]}")
                }

                put("$basePath/{id}") {
                    call.restRespond {
                        val pkValues = call.parsePkValues(pkCols) ?: return@restRespond
                        updateEntity(pkValues)
                    }
                }

                patch("$basePath/{id}/") {
                    call.respondRedirect("../${call.parameters["id"]}")
                }

                patch("$basePath/{id}") {
                    call.restRespond {
                        val pkValues = call.parsePkValues(pkCols) ?: return@restRespond
                        updateEntity(pkValues)
                    }
                }

                delete("$basePath/{id}/") {
                    call.respondRedirect("../${call.parameters["id"]}")
                }

                delete("$basePath/{id}") {
                    call.restRespond {
                        val pkValues = call.parsePkValues(pkCols) ?: return@restRespond
                        val deleted = dbQuery {
                            table.deleteWhere { pkCols eqValues pkValues }
                        }
                        if (deleted == 0) {
                            call.respondError("Not found", HttpStatusCode.NotFound)
                        } else {
                            val result = buildJsonObject { put("status", JsonPrimitive("deleted")) }
                            call.respondText(Json.encodeToString(result), ContentType.Application.Json)
                        }
                    }
                }
            }
        }
    }
}
