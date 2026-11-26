package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SchoolProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val namaDinas: String = "PEMERINTAH PROVINSI JAWA TENGAH / DINAS PENDIDIKAN",
    val namaSekolah: String = "SMA / SMK NEGERI CONTOH NUSANTARA",
    val alamatSekolah: String = "Jl. Pendidikan No. 123, Semarang | Telp. (024) 1234567",
    val namaGuru: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Exam(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val judul: String = "",
    val mapel: String = "",
    val durasiMenit: Long = 45L,
    val active: Boolean = false,
    val tokenSusulan: String = "",
    val questionCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Question(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val examActive: Boolean = false,
    val orderIndex: Long = 0L,
    val pertanyaan: String = "",
    val opsiA: String = "",
    val opsiB: String = "",
    val opsiC: String = "",
    val opsiD: String = "",
    val jawabanBenar: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun optionsList(): List<String> = listOf(opsiA, opsiB, opsiC, opsiD)
}

@Keep
@IgnoreExtraProperties
data class ExamResult(
    @DocumentId val id: String = "",
    val examOwnerId: String = "",
    val studentUid: String = "",
    val nama: String = "",
    val kelas: String = "",
    val studentKey: String = "",
    val skor: Long = 0L,
    val benarCount: Long = 0L,
    val totalCount: Long = 1L,
    val waktuSelesai: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class GeneratedQuestionDraft(
    val pertanyaan: String,
    val opsiA: String,
    val opsiB: String,
    val opsiC: String,
    val opsiD: String,
    val jawabanBenar: String
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
