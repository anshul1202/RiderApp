package com.example.riderapp.data.remote.mock

import com.example.riderapp.data.remote.dto.*
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Hybrid interceptor:
 *  - GET requests to the mocker API → pass through to real network
 *  - All other endpoints (POST create/action/sync) → return mock responses locally
 */
class MockInterceptor : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method

        // ── Real network call for the mocker API endpoint ──
        if (path.startsWith("/mock/")) {
            return chain.proceed(request)
        }

        // ── Mock responses for all other endpoints ──

        // Simulate network delay for mocked endpoints
        Thread.sleep(150)

        return when {

            // GET /api/tasks/{taskId} — single task lookup (served from Room, rarely hit)
            path.matches(Regex("/api/tasks/.+")) && method == "GET" -> {
                createJsonResponse(
                    request, 200,
                    gson.toJson(
                        TaskDto(
                            id = path.substringAfterLast("/"),
                            type = "DROP",
                            status = "ASSIGNED",
                            riderId = "RIDER-001",
                            customerName = "Unknown",
                            customerPhone = "",
                            address = "",
                            description = "",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }

            // POST /api/tasks — create task (echo back the request body)
            path == "/api/tasks" && method == "POST" -> {
                val body = request.body?.let { reqBody ->
                    val buffer = okio.Buffer()
                    reqBody.writeTo(buffer)
                    buffer.readUtf8()
                } ?: "{}"
                createJsonResponse(request, 201, body)
            }

            // POST /api/tasks/{taskId}/actions — submit action
            path.matches(Regex("/api/tasks/.+/actions")) && method == "POST" -> {
                val taskId = path.split("/").dropLast(1).last()
                val stubTask = TaskDto(
                    id = taskId,
                    type = "DROP",
                    status = "ASSIGNED",
                    riderId = "RIDER-001",
                    customerName = "",
                    customerPhone = "",
                    address = "",
                    description = "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                createJsonResponse(request, 200, gson.toJson(stubTask))
            }

            else -> {
                createJsonResponse(request, 404, """{"error":"Not found"}""")
            }
        }
    }

    private fun createJsonResponse(request: okhttp3.Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
