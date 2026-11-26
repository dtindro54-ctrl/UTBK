package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

const val SECRET_HEADER_KEY = "EXAM-SECURE-2026"
const val QR_PREFIX = "EXAMSEC::"
const val CBT_SCHEME = "cbt://exam/"

sealed interface DecodedExamTarget {
    data class CloudExam(val examId: String) : DecodedExamTarget
    data class ExternalServerUrl(val url: String) : DecodedExamTarget
}

object QrCodeUtil {

    fun encodeCloudExamPayload(examId: String): String {
        val raw = "$CBT_SCHEME$examId"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$QR_PREFIX$encoded"
    }

    fun encodeServerUrlPayload(url: String): String {
        val encoded = Base64.encodeToString(url.trim().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$QR_PREFIX$encoded"
    }

    fun decodeTarget(input: String): DecodedExamTarget? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val unwrapped = if (trimmed.startsWith(QR_PREFIX)) {
            val b64 = trimmed.removePrefix(QR_PREFIX).trim()
            try {
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (_: Exception) {
                return null
            }
        } else {
            trimmed
        }

        return when {
            unwrapped.startsWith(CBT_SCHEME) -> {
                val id = unwrapped.removePrefix(CBT_SCHEME).trim()
                if (id.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                    DecodedExamTarget.CloudExam(id)
                } else null
            }
            unwrapped.startsWith("http://", ignoreCase = true) ||
                unwrapped.startsWith("https://", ignoreCase = true) -> {
                DecodedExamTarget.ExternalServerUrl(unwrapped)
            }
            unwrapped.matches(Regex("^[a-zA-Z0-9_\\-]{4,128}$")) -> {
                DecodedExamTarget.CloudExam(unwrapped)
            }
            else -> null
        }
    }

    fun generateQrBitmap(payload: String, sizePx: Int = 640): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) Color.parseColor("#0F2B5B") else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }
}
