package com.surimap.core.fcm

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseMessagingTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (continuation.isActive) {
                            continuation.resume(token.takeIf(String::isNotBlank))
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        } catch (_: IllegalStateException) {
            null
        }
    }

    // 토큰을 지우지 못해도 로그아웃은 끝나야 하므로 실패는 무시한다.
    override suspend fun deleteToken() {
        try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
            }
        } catch (_: IllegalStateException) {
        }
    }
}
