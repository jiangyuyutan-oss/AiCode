package com.aicode.feature.agent.domain.provider

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 触发重试的异常 → 用户可见错误摘要（[Throwable.toRetryErrorInfo]）的分类逻辑。
 */
class RetryPolicyTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "{}".toResponseBody(null)))

    @Test
    fun http_429_maps_to_rate_limit() {
        val info = httpError(429).toRetryErrorInfo()
        assertEquals(RetryErrorKind.RATE_LIMIT, info.kind)
        assertEquals(429, info.statusCode)
    }

    @Test
    fun http_5xx_maps_to_server_error() {
        assertEquals(RetryErrorKind.SERVER_ERROR, httpError(500).toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.SERVER_OVERLOADED, httpError(503).toRetryErrorInfo().kind)
        assertEquals(503, httpError(503).toRetryErrorInfo().statusCode)
        assertEquals(RetryErrorKind.SERVER_ERROR, httpError(502).toRetryErrorInfo().kind)
    }

    @Test
    fun timeout_exceptions_map_to_timeout() {
        assertEquals(RetryErrorKind.TIMEOUT, SocketTimeoutException().toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.TIMEOUT, InterruptedIOException().toRetryErrorInfo().kind)
    }

    @Test
    fun network_exceptions_map_to_specific_kinds() {
        assertEquals(RetryErrorKind.DNS_FAILED, UnknownHostException().toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.CONNECTION_REFUSED, ConnectException().toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.SSL_ERROR, SSLException("handshake failed").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.CONNECTION_RESET, IOException("Connection reset by peer").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.CONNECTION_RESET, IOException("unexpected end of stream").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.NETWORK, IOException("SSE 流被中断").toRetryErrorInfo().kind)
    }

    @Test
    fun stream_api_codes_map_to_specific_kinds() {
        assertEquals(RetryErrorKind.RATE_LIMIT, StreamApiException("rate_limit_exceeded", "m").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.RATE_LIMIT, StreamApiException("insufficient_quota", "m").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.SERVER_OVERLOADED, StreamApiException("server_is_overloaded", "m").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.SERVER_ERROR, StreamApiException("internal_error", "m").toRetryErrorInfo().kind)
        assertEquals(RetryErrorKind.UNKNOWN, StreamApiException("some_other", "m").toRetryErrorInfo().kind)
    }

    @Test
    fun status_code_null_for_non_http_errors() {
        assertNull(SocketTimeoutException().toRetryErrorInfo().statusCode)
        assertNull(IOException().toRetryErrorInfo().statusCode)
        assertNull(StreamApiException("server_is_overloaded", "m").toRetryErrorInfo().statusCode)
    }

    // ── streamWithStaircaseRetry 计数与终止 ─────────────────────

    @Test
    fun streamRetry_countsUpAndThrowsAfterMax() = runTest {
        val attempts = mutableListOf<Int>()
        var calls = 0
        val e = assertThrowsStreamError {
            streamWithStaircaseRetry(
                attemptOnce = { onContent ->
                    calls++
                    throw IOException("stream reset by peer")
                },
                onRetry = { attempt, max, _ ->
                    attempts += attempt
                    assertEquals(MAX_NETWORK_RETRIES, max)
                }
            )
        }
        // 初次尝试 + MAX_NETWORK_RETRIES 次重试后仍未成功 → 抛出
        assertEquals(MAX_NETWORK_RETRIES + 1, calls)
        // 计数从 1 真实累计到 MAX_NETWORK_RETRIES，不被打回 1
        assertEquals((1..MAX_NETWORK_RETRIES).toList(), attempts)
        assertTrue(e is IOException)
    }

    @Test
    fun streamRetry_receivedContentThenReset_doesNotLoopForever() = runTest {
        // 每次尝试都「收到过内容」再断流：旧实现会把 attempt 重置回 0 导致永远 1/6 死循环。
        // 回归断言：计数仍真实累计、最终在 MAX_NETWORK_RETRIES 次重试后抛出。
        val attempts = mutableListOf<Int>()
        var calls = 0
        assertThrowsStreamError {
            streamWithStaircaseRetry(
                attemptOnce = { onContent ->
                    calls++
                    onContent() // 模拟收到了内容块（连接已恢复）
                    throw IOException("unexpected end of stream")
                },
                onRetry = { attempt, max, _ ->
                    attempts += attempt
                    assertEquals(MAX_NETWORK_RETRIES, max)
                }
            )
        }
        assertEquals(MAX_NETWORK_RETRIES + 1, calls)
        assertEquals((1..MAX_NETWORK_RETRIES).toList(), attempts)
    }

    @Test
    fun streamRetry_succeedsAfterRetries_returnsNormally() = runTest {
        var calls = 0
        streamWithStaircaseRetry(
            attemptOnce = { onContent ->
                calls++
                if (calls < 3) {
                    onContent()
                    throw IOException("econnreset")
                } else {
                    onContent()
                }
            }
        )
        assertEquals(3, calls)
    }

    @Test
    fun streamRetry_nonRetriable_doesNotRetry() = runTest {
        var calls = 0
        assertThrowsStreamError {
            streamWithStaircaseRetry(
                attemptOnce = {
                    calls++
                    throw StreamApiException("invalid_request_error", "bad request")
                }
            )
        }
        assertEquals(1, calls)
    }

    private inline fun assertThrowsStreamError(block: () -> Unit): Throwable {
        var caught: Throwable? = null
        try {
            block()
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue("expected exception to be thrown", caught != null)
        return caught!!
    }
}
