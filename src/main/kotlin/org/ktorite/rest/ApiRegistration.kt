package org.ktorite.rest

import org.jetbrains.exposed.v1.core.Table

data class ApiRegistration(
    val table: Table,
    var readOnly: Boolean = false,
    var paginated: Boolean = true,
    var path: String = table.tableName.lowercase(),
    var pageSize: Int = 20,
    var maxPageSize: Int = 100
)
