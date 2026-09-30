package com.yuvraj.resumescreener.data.remote

/**
 * Failure modes the UI must distinguish, because each needs a different
 * response. Retrying a rate limit makes sense; retrying a bad key does not.
 *
 * Messages are provider-neutral on purpose. These surface to the user, and the
 * app may be pointed at Gemini or at an OpenAI-compatible endpoint, so a
 * message naming Gemini would be actively misleading on the other path.
 *
 * Where the server explains itself, that explanation is carried in `detail` and
 * preferred over our own wording. "API key is inactive" tells a user far more
 * than "the key was rejected".
 */
sealed class LlmException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The API key is missing, malformed, rejected or inactive. */
    class InvalidKey(val detail: String? = null, cause: Throwable? = null) :
        LlmException(detail ?: "The API key was rejected by the provider.", cause)

    /** 429. Worth retrying on a soft limit; a hard quota wall just repeats. */
    class RateLimited(val detail: String? = null, cause: Throwable? = null) :
        LlmException(detail ?: "Rate limit reached. Try again shortly.", cause)

    /** 5xx. Retryable, matching the source pipeline's max_retries of 2. */
    class ServerUnavailable(val code: Int, cause: Throwable? = null) :
        LlmException("The provider returned $code.", cause)

    /**
     * The model is retired or not offered to this account.
     *
     * Separate from [MalformedResponse] because the fix differs: the user picks
     * another model in Settings rather than retrying.
     */
    class ModelUnavailable(val model: String, val detail: String) :
        LlmException("$model is unavailable: $detail")

    /** Transport failure: no connectivity, timeout, DNS. */
    class Network(cause: Throwable? = null) :
        LlmException("Could not reach the model provider.", cause)

    /**
     * The endpoint itself is unusable: wrong base URL, or a host that did not
     * answer at the expected path.
     *
     * Distinct from [Network] because the fix is the URL, not the network. This
     * is the most likely mistake with a custom endpoint, so the message says so.
     */
    class Unreachable(detail: String, cause: Throwable? = null) :
        LlmException("Endpoint not reachable: $detail", cause)

    /** 200, but the payload did not match the expected schema. */
    class MalformedResponse(val detail: String, cause: Throwable? = null) :
        LlmException("Unexpected response: $detail", cause)

    /** The request was blocked, typically by a safety filter. */
    class Blocked(val reason: String) :
        LlmException("The request was blocked by the provider: $reason")
}
