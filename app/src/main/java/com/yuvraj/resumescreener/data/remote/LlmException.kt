package com.yuvraj.resumescreener.data.remote

/**
 * Failure modes the UI must distinguish, because each needs a different
 * response. Retrying a rate limit makes sense; retrying a bad key does not.
 */
sealed class LlmException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The API key is missing, malformed, or rejected. Send the user to Settings. */
    class InvalidKey(cause: Throwable? = null) :
        LlmException("The Gemini API key was rejected.", cause)

    /** 429. Retryable on quota, but retrying a hard quota limit just wastes time. */
    class RateLimited(cause: Throwable? = null) :
        LlmException("Gemini rate limit reached. Try again shortly.", cause)

    /** 5xx. Retryable, matching the source's max_retries of 2. */
    class ServerUnavailable(val code: Int, cause: Throwable? = null) :
        LlmException("Gemini returned $code.", cause)

    /** Transport failure: no connectivity, timeout, DNS. */
    class Network(cause: Throwable? = null) :
        LlmException("Could not reach the model endpoint.", cause)

    /**
     * The endpoint itself is not usable: a wrong base URL, a host that did not
     * answer at the expected path, or a refused connection.
     *
     * Distinct from [Network] because the fix is the URL, not the network.
     * This is the most likely user error with a custom endpoint.
     */
    class Unreachable(detail: String, cause: Throwable? = null) :
        LlmException("Endpoint not reachable: $detail", cause)

    /**
     * 404: the model is retired or not available to this key.
     *
     * Separate from [MalformedResponse] because the fix is different: the
     * user needs to pick a different model in Settings, not retry.
     */
    class ModelUnavailable(val model: String, val detail: String) :
        LlmException("The model $model is unavailable: $detail")

    /** 200, but the payload did not match the expected schema. */
    class MalformedResponse(detail: String, cause: Throwable? = null) :
        LlmException("Gemini returned an unexpected response: $detail", cause)

    /** The request was blocked, typically by a safety filter. */
    class Blocked(val reason: String) :
        LlmException("The request was blocked by Gemini: $reason")
}
