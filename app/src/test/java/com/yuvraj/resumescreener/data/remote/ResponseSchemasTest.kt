package com.yuvraj.resumescreener.data.remote

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The schemas are the contract with the model, and a provider is free to omit
 * anything not marked required. Each field the UI depends on must be required,
 * or Kotlin's defaults will silently substitute zeros.
 */
class ResponseSchemasTest {

    @Test
    fun `match result requires the breakdown`() = run {
        // Regression guard from a live endpoint: with breakdown left optional
        // the model returned a score and summary but no breakdown, and the UI
        // showed three empty bars instead of an error.
        val required = ResponseSchemas.matchResult.requiredStrings()
        assertTrue(
            "breakdown must be required or the model may omit it: $required",
            "breakdown" in required,
        )
    }

    @Test
    fun `every declared property is required so nothing defaults to zero`() {
        listOf(
            "matchResult" to ResponseSchemas.matchResult,
            "resume" to ResponseSchemas.resume,
            "jobDescription" to ResponseSchemas.jobDescription,
        ).forEach { (name, schema) ->
            val properties = schema["properties"]!!.jsonObject.keys
            val required = schema.requiredStrings()
            val missing = properties - required.toSet()
            assertTrue(
                "$name leaves $missing optional, so the model may omit them and " +
                    "Kotlin will substitute empty defaults",
                missing.isEmpty(),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.requiredStrings(): List<String> =
        (this["required"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.jsonPrimitive.content } ?: emptyList()
}
