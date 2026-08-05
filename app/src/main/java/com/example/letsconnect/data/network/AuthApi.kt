package com.example.letsconnect.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import java.io.File

// ── NEW: Data class wrapper to send the ID Token via JSON body ───────────────
//@kotlinx.serialization.Serializable
//data class GoogleLoginRequest(val idToken: String)

sealed interface AuthResult {
    data class Success(
        val userId: UInt,
        val fullName: String,
        val username: String
    ) : AuthResult
    data class Error(val message: String) : AuthResult
}

sealed interface MessageResult {
    data class Success(val message: Message) : MessageResult
    data class SuccessList(val messages: List<Message>) : MessageResult
    data class Error(val message: String) : MessageResult
}

object UserSession {
    var userId: UInt? = null
    var fullName: String? = null
    var username: String? = null
}

private interface LetsConnectService {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("users")
    suspend fun signUp(@Body request: SignUpRequest): AuthResponse

    // ── NEW: Retrofit endpoint mapping for Google Auth ────────────────────────
    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): AuthResponse

    @GET("users")
    suspend fun getUsers(): List<ExposedUser>

    @POST("messages")
    suspend fun sendMessage(
        @Query("senderId") senderId: UInt,
        @Body request: SendMessageRequest
    ): Message

    @Multipart
    @POST("messages/voice")
    suspend fun sendVoiceMessage(
        @Part("senderId") senderId: okhttp3.RequestBody,
        @Part("receiverId") receiverId: okhttp3.RequestBody,
        @Part("duration") duration: okhttp3.RequestBody,
        @Part file: MultipartBody.Part
    ): Message

    @PUT("messages/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: UInt,
        @Body request: UpdateMessageRequest
    ): Message

    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: UInt)

    @DELETE("messages/conversation/{userId1}/{userId2}")
    suspend fun deleteConversation(
        @Path("userId1") userId1: UInt,
        @Path("userId2") userId2: UInt
    )

    @GET("messages/conversation/{userId1}/{userId2}")
    suspend fun getConversation(
        @Path("userId1") userId1: UInt,
        @Path("userId2") userId2: UInt
    ): List<Message>
}

object ServerConfig {
    const val BASE_URL = "http://192.168.68.63:8080/"
}

private object RetrofitClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: LetsConnectService by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LetsConnectService::class.java)
    }
}

object AuthApi {
    suspend fun login(username: String, password: String): AuthResult {
        return runAuthCall { RetrofitClient.apiService.login(LoginRequest(username, password)) }
    }

    suspend fun signUp(fullName: String, username: String, password: String): AuthResult {
        return runAuthCall { RetrofitClient.apiService.signUp(SignUpRequest(fullName, username, password)) }
    }

    // ── NEW: Public method your Login screen will call ───────────────────────
    suspend fun googleLogin(idToken: String): AuthResult {
        return runAuthCall { RetrofitClient.apiService.googleLogin(GoogleLoginRequest(idToken)) }
    }

    suspend fun getUsers(): List<ExposedUser> {
        return try {
            RetrofitClient.apiService.getUsers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun runAuthCall(call: suspend () -> AuthResponse): AuthResult {
        return try {
            val response = call()
            AuthResult.Success(
                userId = response.id,
                fullName = response.fullName,
                username = response.username
            )
        } catch (e: HttpException) {
            AuthResult.Error(e.readErrorMessage())
        } catch (e: Exception) {
            AuthResult.Error("Could not connect to the server.")
        }
    }
}

object MessagesApi {
    suspend fun getConversation(currentUserId: UInt, receiverId: UInt): MessageResult {
        return try {
            MessageResult.SuccessList(
                RetrofitClient.apiService.getConversation(currentUserId, receiverId)
            )
        } catch (e: Exception) {
            MessageResult.Error("Could not load messages.")
        }
    }

    suspend fun sendMessage(senderId: UInt, receiverId: UInt, content: String): MessageResult {
        return try {
            MessageResult.Success(
                RetrofitClient.apiService.sendMessage(
                    senderId = senderId,
                    request = SendMessageRequest(senderId, receiverId, content)
                )
            )
        } catch (e: Exception) {
            MessageResult.Error("Could not send message.")
        }
    }

    suspend fun sendVoiceMessage(senderId: UInt, receiverId: UInt, file: File, duration: String): MessageResult {
        return try {
            val textType = "text/plain".toMediaType()
            val audioBody = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("file", file.name, audioBody)

            MessageResult.Success(
                RetrofitClient.apiService.sendVoiceMessage(
                    senderId = senderId.toString().toRequestBody(textType),
                    receiverId = receiverId.toString().toRequestBody(textType),
                    duration = duration.toRequestBody(textType),
                    file = audioPart
                )
            )
        } catch (e: Exception) {
            MessageResult.Error("Could not send voice message.")
        }
    }

    suspend fun editMessage(messageId: UInt, content: String): MessageResult {
        return try {
            MessageResult.Success(
                RetrofitClient.apiService.editMessage(
                    messageId = messageId,
                    request = UpdateMessageRequest(content)
                )
            )
        } catch (e: Exception) {
            MessageResult.Error("Could not edit message.")
        }
    }

    suspend fun deleteMessage(messageId: UInt): MessageResult {
        return try {
            RetrofitClient.apiService.deleteMessage(messageId)
            MessageResult.Success(
                Message(
                    id = messageId,
                    senderId = 0u,
                    receiverId = 0u,
                    content = "",
                    timestamp = ""
                )
            )
        } catch (e: Exception) {
            MessageResult.Error("Could not delete message.")
        }
    }

    suspend fun deleteConversation(currentUserId: UInt, otherUserId: UInt): MessageResult {
        return try {
            RetrofitClient.apiService.deleteConversation(currentUserId, otherUserId)
            MessageResult.SuccessList(emptyList())
        } catch (e: Exception) {
            MessageResult.Error("Could not delete conversation.")
        }
    }
}

private fun HttpException.readErrorMessage(): String {
    val fallback = when (code()) {
        401 -> "Username or password is wrong."
        409 -> "That username is already taken."
        else -> "Something went wrong. Please try again."
    }
    val rawBody = response()?.errorBody()?.string() ?: return fallback
    return runCatching { Json.decodeFromString<ErrorResponse>(rawBody).error }.getOrDefault(fallback)
}
