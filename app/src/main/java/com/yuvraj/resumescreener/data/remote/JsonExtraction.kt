package com.yuvraj.resumescreener.data.remote

/**
 * Pulls a JSON object out of a model response.
 *
 * Schema-constrained decoding usually returns clean JSON, but a reasoning model
 * served through an OpenAI-compatible endpoint may wrap the answer in prose or
 * a fenced code block before the object. Rather than trust that never happens,
 * every provider funnels through here so one bad fence is not a parse error the
 * user sees as a failure.
 */
internal object JsonExtraction {

    private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val LEADING_NOISE = Regex("^[\\s\\S]*?(?=\\{)")
    private val TRAILING_NOISE = Regex("(?<=\\})[\\s\\S]*$")

    /** The first balanced top-level object in [raw]. */
    fun firstObject(raw: String): String? {
        val trimmed = raw.trim()
        FENCE.find(trimmed)?.groupValues?.getOrNull(1)?.let { fenced ->
            extract(fenced)?.let { return it }
        }
        return extract(trimmed)
    }

    private fun extract(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        val end = lastBalancedBrace(text, start) ?: return null
        return text.substring(start, end + 1)
    }

    /** Walk the string tracking brace depth, ignoring braces inside literals. */
    private fun lastBalancedBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
