package org.ktorite.rest

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

class RestConfig(models: List<Table>) {
    @PublishedApi internal val registeredModels = models
    @PublishedApi internal val registrations = mutableListOf<ApiRegistration>()
    @PublishedApi internal val serializers = mutableMapOf<Class<*>, ColumnSerializer>()


    /**
     *  Creates a default [ApiRegistration] for every registered model.
     */
    fun registerAll() {
        registeredModels.forEach { table ->
            registrations += ApiRegistration(table)
        }
    }

    /**
     * Registers a REST API for [T] with custom configuration.
     *
     * @param block configure the [ApiRegistration] (path, page size, readOnly, etc.)
     * @throws IllegalStateException if [T] wasn't passed to [KtoriteConfig.registerModels]
     */
    inline fun <reified T : Table> register(block: ApiRegistration.() -> Unit = {}) {
        val table = registeredModels.filterIsInstance<T>().firstOrNull()
            ?: error("${T::class.simpleName} is not registered. Call registerModels() first.")
        registrations += ApiRegistration(table).apply(block)
    }

    /**
     * Registers a custom JSON serializer for a column type.
     *
     * @param serializer converts between [T] and JSON
     */
    inline fun <reified T : ColumnType<*>> registerSerializer(serializer: ColumnSerializer) {
        serializers[T::class.java] = serializer
    }
}
