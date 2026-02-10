package com.example.riderapp.data.remote.api

import com.example.riderapp.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface TaskApi {

    /**
     * Fetch tasks from the real mocker API.
     * URL: https://free.mockerapi.com/mock/97cb8e56-ab2a-48be-812e-47188382969b
     * Query params are sent but the mock endpoint may ignore them.
     */
    @GET("mock/97cb8e56-ab2a-48be-812e-47188382969b")
    suspend fun getTasks(
        @Query("riderId") riderId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<PaginatedResponse<TaskDto>>

    @GET("api/tasks/{taskId}")
    suspend fun getTask(
        @Path("taskId") taskId: String
    ): Response<TaskDto>

    @POST("api/tasks")
    suspend fun createTask(
        @Body task: TaskDto
    ): Response<TaskDto>

    @POST("api/tasks/{taskId}/actions")
    suspend fun submitAction(
        @Path("taskId") taskId: String,
        @Body action: TaskActionDto
    ): Response<TaskDto>

    /**
     * Batch sync actions to the real mocker API.
     * URL: https://free.mockerapi.com/mock/04ea364c-f3c6-4c90-8cf3-fa3f672d38d1
     */
    @POST("mock/04ea364c-f3c6-4c90-8cf3-fa3f672d38d1")
    suspend fun syncActions(
        @Body request: SyncRequest
    ): Response<SyncResponse>
}
