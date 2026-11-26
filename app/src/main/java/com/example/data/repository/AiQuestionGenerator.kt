package com.example.data.repository

import android.util.Log
import com.example.data.model.GeneratedQuestionDraft
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiQuestionGenerator"
private const val MAX_INLINE_PDF_BYTES = 18 * 1024 * 1024 // 18MB safety ceiling (< 20MB limit)

class AiQuestionGenerator {

    private val questionSchema = Schema.obj(
        properties = mapOf(
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "pertanyaan" to Schema.string(description = "Teks soal pilihan ganda yang jelas dalam Bahasa Indonesia"),
                        "opsiA" to Schema.string(description = "Pilihan A diawali 'A. '"),
                        "opsiB" to Schema.string(description = "Pilihan B diawali 'B. '"),
                        "opsiC" to Schema.string(description = "Pilihan C diawali 'C. '"),
                        "opsiD" to Schema.string(description = "Pilihan D diawali 'D. '"),
                        "jawabanBenar" to Schema.string(description = "Harus persis sama dengan salah satu teks dari opsiA, opsiB, opsiC, atau opsiD")
                    )
                ),
                description = "Daftar soal pilihan ganda yang dihasilkan dari materi"
            )
        )
    )

    private val config = generationConfig {
        responseMimeType = "application/json"
        responseSchema = questionSchema
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun generateQuestionsFromPdfOrText(
        judul: String,
        mapel: String,
        jumlah: Int,
        pdfBytes: ByteArray?,
        rawMaterialText: String
    ): Result<List<GeneratedQuestionDraft>> {
        val safeCount = jumlah.coerceIn(1, 30)
        if (pdfBytes == null && rawMaterialText.isBlank()) {
            return Result.failure(IllegalArgumentException("Pilih file PDF atau masukkan teks materi terlebih dahulu."))
        }
        if (pdfBytes != null && pdfBytes.size > MAX_INLINE_PDF_BYTES) {
            return Result.failure(IllegalArgumentException("Ukuran file PDF melebihi batas 18 MB."))
        }

        val instruction = buildString {
            appendLine("Buatkan tepat $safeCount soal pilihan ganda berbahasa Indonesia berkualitas tinggi untuk ujian '$judul' mata pelajaran '$mapel'.")
            appendLine("Aturan wajib:")
            appendLine("1. Setiap soal memiliki 4 pilihan: opsiA ('A. ...'), opsiB ('B. ...'), opsiC ('C. ...'), opsiD ('D. ...').")
            appendLine("2. Field 'jawabanBenar' HARUS sama persis karakter-demi-karakter dengan salah satu opsi (opsiA/opsiB/opsiC/opsiD).")
            appendLine("3. Soal harus bervariasi, akurat sesuai materi, dan tidak ambigu.")
            if (rawMaterialText.isNotBlank()) {
                appendLine()
                appendLine("Materi tambahan:")
                appendLine(rawMaterialText.take(18000))
            }
        }

        return try {
            val response = withTimeout(60_000L) {
                if (pdfBytes != null) {
                    model.generateContent(
                        content {
                            inlineData(pdfBytes, "application/pdf")
                            text(instruction)
                        }
                    )
                } else {
                    model.generateContent(instruction)
                }
            }

            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini mengembalikan respons kosong. Silakan coba lagi."))
            }

            val root = JSONObject(jsonText)
            val arr = root.getJSONArray("questions")
            val drafts = mutableListOf<GeneratedQuestionDraft>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val pertanyaan = item.optString("pertanyaan", "").trim().take(1000)
                val opsiA = item.optString("opsiA", "A. -").trim().ifBlank { "A. -" }.take(400)
                val opsiB = item.optString("opsiB", "B. -").trim().ifBlank { "B. -" }.take(400)
                val opsiC = item.optString("opsiC", "C. -").trim().ifBlank { "C. -" }.take(400)
                val opsiD = item.optString("opsiD", "D. -").trim().ifBlank { "D. -" }.take(400)
                val rawKey = item.optString("jawabanBenar", opsiA).trim().take(400)
                val validOptions = listOf(opsiA, opsiB, opsiC, opsiD)
                val matchedKey = when {
                    rawKey in validOptions -> rawKey
                    rawKey.startsWith("A", ignoreCase = true) -> opsiA
                    rawKey.startsWith("B", ignoreCase = true) -> opsiB
                    rawKey.startsWith("C", ignoreCase = true) -> opsiC
                    rawKey.startsWith("D", ignoreCase = true) -> opsiD
                    else -> opsiA
                }
                if (pertanyaan.isNotBlank()) {
                    drafts.add(
                        GeneratedQuestionDraft(
                            pertanyaan = pertanyaan,
                            opsiA = opsiA,
                            opsiB = opsiB,
                            opsiC = opsiC,
                            opsiD = opsiD,
                            jawabanBenar = matchedKey
                        )
                    )
                }
            }

            if (drafts.isEmpty()) {
                Result.failure(IllegalStateException("AI tidak menghasilkan soal yang valid dari materi tersebut."))
            } else {
                Result.success(drafts)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI question generation timed out", e)
            Result.failure(IllegalStateException("Waktu pemrosesan AI habis (60 detik). Coba kurangi jumlah soal atau ukuran PDF."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI question generation failed", e)
            Result.failure(e)
        }
    }
}
