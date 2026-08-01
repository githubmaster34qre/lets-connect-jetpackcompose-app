package com.example.letsconnect.network

import java.io.File

object VoiceApi {
    suspend fun uploadVoice(
        senderId: UInt,
        receiverId: UInt,
        file: File,
        duration: String
    ): MessageResult {
        return MessagesApi.sendVoiceMessage(senderId, receiverId, file, duration)
    }
}
