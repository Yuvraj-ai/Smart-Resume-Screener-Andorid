package com.yuvraj.resumescreener.data.remote

/**
 * Failure modes the UI must distinguish, because each needs a different
 * response. Retrying a rate limit makes sense; retrying a bad key does not.
 */
sealed class GeminiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The API key is missing, malformed, or rejected. Send the user to Settings. */
    class InvalidKey(cause: Throwable? = null) :
        GeminiException("The Gemini API key was rejected.", cause)

    /** 429. Retryable on quota, but retrying a hard quota limit just wastes time. */
    class RateLimited(cause: Throwable? = null) :
        GeminiException("Gemini rate limit reached. Try again shortly.", cause)

    /** 5xx. Retryable, matching the source's max_retries of 2. */
    class ServerUnavailable(val code: Int, cause: Throwable? = null) :
        GeminiException("Gemini returned $code.", cause)

    /** Transport failure: no connectivity, timeout, DNS. */
    class Network(cause: Throwable? = null) :
        GeminiException("Could not reach Gemini.", cause)

    /**
     * 404: the model is retired or not available to this key.
     *
     * Separate from [MalformedResponse] because the fix is different: the
     * user needs to pick a different model in Settings, not retry.
     */
    class ModelUnavailable(val model: String, val detail: String) :
        GeminiException("The model $model is unavailable: $detail")

    /** 200, but the payload did not match the expected schema. */
    class MalformedResponse(detail: String, cause: Throwable? = null) :
        GeminiException("Gemini returned an unexpected response: $detail", cause)

    /** The request was blocked, typically by a safety filter. */
    class Blocked(val reason: String) :
        GeminiException("The request was blocked by Gemini: $reason")
}
