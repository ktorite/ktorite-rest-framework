package org.ktorite.rest

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*

internal fun jsonError(message: String, status: HttpStatusCode): String {
    return Json.encodeToString(buildJsonObject { put("error", JsonPrimitive(message)) })
}

internal suspend fun ApplicationCall.respondError(message: String, status: HttpStatusCode) {
    respondText(jsonError(message, status), ContentType.Application.Json, status)
}

internal suspend fun ApplicationCall.respondFieldErrors(errors: List<FieldError>, status: HttpStatusCode) {
    respondText(
        Json.encodeToString(buildJsonObject {
            put("errors", JsonArray(errors.map { error ->
                buildJsonObject {
                    error.field?.let { put("field", JsonPrimitive(it)) }
                    put("message", JsonPrimitive(error.message))
                }
            }))
        }),
        ContentType.Application.Json,
        status
    )
}
