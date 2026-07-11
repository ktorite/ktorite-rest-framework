package org.ktorite.rest

import org.jetbrains.exposed.v1.core.*

internal fun pkColumns(table: Table): List<Column<*>> =
    table.primaryKey?.columns?.toList()
        ?: error("Table ${table.tableName} has no primary key defined")

internal infix fun List<Column<*>>.eqValues(values: List<Any?>): Op<Boolean> {
    require(size == values.size) { "pkCols.size (${size}) != values.size (${values.size})" }
    val conditions = this.zip(values) { col, value -> col eqLiteral value }
    return AndOp(conditions)
}

internal infix fun Column<*>.eqLiteral(value: Any?): Op<Boolean> {
    @Suppress("UNCHECKED_CAST")
    val col = this as Column<Any?>
    val lit = LiteralOp(col.columnType, value)
    return EqOp(col, lit)
}
