package org.ktorite.rest

import io.ktor.server.application.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.Table

data class ApiRegistration(
    val table: Table,
    var readOnly: Boolean = false,
    var paginated: Boolean = true,
    var path: String = table.tableName.lowercase(),
    var pageSize: Int = 20,
    var maxPageSize: Int = 100,
    var beforeCreate: (suspend (ApplicationCall, JsonObject) -> JsonObject?)? = null,
    var afterCreate: (suspend (ApplicationCall, JsonArray) -> Unit)? = null,
    var beforeUpdate: (suspend (ApplicationCall, String, JsonObject) -> JsonObject?)? = null,
    var afterUpdate: (suspend (ApplicationCall, String) -> Unit)? = null,
    var beforeDelete: (suspend (ApplicationCall, String) -> Boolean)? = null,
    var afterDelete: (suspend (ApplicationCall, String) -> Unit)? = null
)
