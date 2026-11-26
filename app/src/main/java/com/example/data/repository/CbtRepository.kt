package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Exam
import com.example.data.model.ExamResult
import com.example.data.model.GeneratedQuestionDraft
import com.example.data.model.Question
import com.example.data.model.SchoolProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DuplicateSubmissionException(message: String) : Exception(message)

class CbtRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses database.")
    }

    // --- SCHOOL PROFILE (KOP SURAT) ---

    fun observeSchoolProfile(userId: String): Flow<SchoolProfile> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            SchoolProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: SchoolProfile(id = userId, userId = userId)
                    } else {
                        SchoolProfile(id = userId, userId = userId)
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveSchoolProfile(
        namaDinas: String,
        namaSekolah: String,
        alamatSekolah: String,
        namaGuru: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val userId = requireUserId()
            val cleanDinas = namaDinas.trim().take(150)
            val cleanSekolah = namaSekolah.trim().take(150)
            val cleanAlamat = alamatSekolah.trim().take(250)
            val cleanGuru = namaGuru.trim().take(120)
            require(cleanDinas.isNotBlank()) { "Nama Dinas tidak boleh kosong." }
            require(cleanSekolah.isNotBlank()) { "Nama Sekolah tidak boleh kosong." }
            require(cleanAlamat.isNotBlank()) { "Alamat Sekolah tidak boleh kosong." }

            val docRef = db.collection("users").document(userId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "namaDinas" to cleanDinas,
                        "namaSekolah" to cleanSekolah,
                        "alamatSekolah" to cleanAlamat,
                        "namaGuru" to cleanGuru,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "namaDinas" to cleanDinas,
                        "namaSekolah" to cleanSekolah,
                        "alamatSekolah" to cleanAlamat,
                        "namaGuru" to cleanGuru,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- TEACHER EXAMS ---

    fun observeTeacherExams(userId: String): Flow<List<Exam>> = flow {
        val path = "exams"
        emitAll(
            db.collection("exams")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Exam::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createExamWithQuestions(
        judul: String,
        mapel: String,
        durasiMenit: Int,
        questions: List<GeneratedQuestionDraft>,
        setAsActive: Boolean = true
    ): Result<String> {
        var path = "exams"
        return try {
            val uid = requireUserId()
            val cleanJudul = judul.trim().take(150)
            val cleanMapel = mapel.trim().take(100)
            val safeDurasi = durasiMenit.coerceIn(1, 300)
            require(cleanJudul.isNotBlank()) { "Judul ujian tidak boleh kosong." }
            require(cleanMapel.isNotBlank()) { "Mata pelajaran tidak boleh kosong." }
            require(questions.size <= 100) { "Maksimal 100 soal per paket ujian." }

            if (setAsActive) {
                deactivateAllTeacherExams(uid)
            }

            val examRef = db.collection("exams").document()
            path = examRef.path

            val examPayload = mapOf(
                "ownerId" to uid,
                "judul" to cleanJudul,
                "mapel" to cleanMapel,
                "durasiMenit" to safeDurasi,
                "active" to setAsActive,
                "tokenSusulan" to "",
                "questionCount" to questions.size,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            examRef.set(examPayload).await()

            if (questions.isNotEmpty()) {
                val batch = db.batch()
                questions.forEachIndexed { index, q ->
                    val qRef = examRef.collection("questions").document()
                    batch.set(
                        qRef,
                        mapOf(
                            "ownerId" to uid,
                            "examActive" to setAsActive,
                            "orderIndex" to index,
                            "pertanyaan" to q.pertanyaan.trim().ifBlank { "Pertanyaan ${index + 1}" }.take(1000),
                            "opsiA" to q.opsiA.trim().ifBlank { "A. -" }.take(400),
                            "opsiB" to q.opsiB.trim().ifBlank { "B. -" }.take(400),
                            "opsiC" to q.opsiC.trim().ifBlank { "C. -" }.take(400),
                            "opsiD" to q.opsiD.trim().ifBlank { "D. -" }.take(400),
                            "jawabanBenar" to q.jawabanBenar.trim().ifBlank { q.opsiA.trim().ifBlank { "A. -" } }.take(400),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(examRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun deactivateAllTeacherExams(uid: String) {
        val activeExams = db.collection("exams")
            .whereEqualTo("ownerId", uid)
            .get()
            .await()
            .documents
            .filter { it.getBoolean("active") == true }

        for (examDoc in activeExams) {
            val batch = db.batch()
            batch.update(
                examDoc.reference,
                mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp())
            )
            val qDocs = examDoc.reference.collection("questions")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .documents
            for (qDoc in qDocs) {
                batch.update(
                    qDoc.reference,
                    mapOf("examActive" to false, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
        }
    }

    suspend fun activateExam(examId: String): Result<Unit> {
        val path = "exams/$examId"
        return try {
            val uid = requireUserId()
            require(examId.isNotBlank()) { "ID ujian tidak valid." }

            deactivateAllTeacherExams(uid)

            val examRef = db.collection("exams").document(examId)
            val batch = db.batch()
            batch.update(
                examRef,
                mapOf("active" to true, "updatedAt" to FieldValue.serverTimestamp())
            )
            val qDocs = examRef.collection("questions")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .documents
            for (qDoc in qDocs) {
                batch.update(
                    qDoc.reference,
                    mapOf("examActive" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSusulanToken(examId: String, tokenSusulan: String): Result<Unit> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            val cleanToken = tokenSusulan.trim().take(64)
            db.collection("exams").document(examId).update(
                mapOf(
                    "tokenSusulan" to cleanToken,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(examId: String): Result<Unit> {
        val path = "exams/$examId"
        return try {
            val uid = requireUserId()
            val examRef = db.collection("exams").document(examId)

            val qDocs = examRef.collection("questions")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .documents
            val rDocs = examRef.collection("results")
                .whereEqualTo("examOwnerId", uid)
                .get()
                .await()
                .documents

            val allRefs = qDocs.map { it.reference } + rDocs.map { it.reference } + listOf(examRef)
            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- QUESTIONS ---

    fun observeTeacherQuestions(examId: String, userId: String): Flow<List<Question>> = flow {
        val path = "exams/$examId/questions"
        emitAll(
            db.collection("exams").document(examId).collection("questions")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Question::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.orderIndex }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateQuestion(
        examId: String,
        questionId: String,
        orderIndex: Int,
        pertanyaan: String,
        opsiA: String,
        opsiB: String,
        opsiC: String,
        opsiD: String,
        jawabanBenar: String
    ): Result<Unit> {
        val path = "exams/$examId/questions/$questionId"
        return try {
            requireUserId()
            require(pertanyaan.isNotBlank()) { "Teks pertanyaan tidak boleh kosong." }
            require(opsiA.isNotBlank() && opsiB.isNotBlank() && opsiC.isNotBlank() && opsiD.isNotBlank()) {
                "Semua pilihan A-D harus diisi."
            }
            require(jawabanBenar.isNotBlank()) { "Kunci jawaban tidak boleh kosong." }

            db.collection("exams").document(examId)
                .collection("questions").document(questionId)
                .update(
                    mapOf(
                        "orderIndex" to orderIndex.coerceIn(0, 200),
                        "pertanyaan" to pertanyaan.trim().take(1000),
                        "opsiA" to opsiA.trim().take(400),
                        "opsiB" to opsiB.trim().take(400),
                        "opsiC" to opsiC.trim().take(400),
                        "opsiD" to opsiD.trim().take(400),
                        "jawabanBenar" to jawabanBenar.trim().take(400),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addManualQuestion(
        exam: Exam,
        orderIndex: Int,
        draft: GeneratedQuestionDraft
    ): Result<Unit> {
        val path = "exams/${exam.id}/questions"
        return try {
            val uid = requireUserId()
            val examRef = db.collection("exams").document(exam.id)
            val qRef = examRef.collection("questions").document()
            val batch = db.batch()
            batch.set(
                qRef,
                mapOf(
                    "ownerId" to uid,
                    "examActive" to exam.active,
                    "orderIndex" to orderIndex.coerceIn(0, 200),
                    "pertanyaan" to draft.pertanyaan.trim().take(1000),
                    "opsiA" to draft.opsiA.trim().take(400),
                    "opsiB" to draft.opsiB.trim().take(400),
                    "opsiC" to draft.opsiC.trim().take(400),
                    "opsiD" to draft.opsiD.trim().take(400),
                    "jawabanBenar" to draft.jawabanBenar.trim().take(400),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                examRef,
                mapOf(
                    "questionCount" to (exam.questionCount + 1).coerceAtMost(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuestion(exam: Exam, questionId: String): Result<Unit> {
        val path = "exams/${exam.id}/questions/$questionId"
        return try {
            requireUserId()
            val examRef = db.collection("exams").document(exam.id)
            val qRef = examRef.collection("questions").document(questionId)
            val batch = db.batch()
            batch.delete(qRef)
            batch.update(
                examRef,
                mapOf(
                    "questionCount" to (exam.questionCount - 1).coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- RESULTS (TEACHER VIEW + BUKA KUNCI) ---

    fun observeTeacherResults(examId: String, userId: String): Flow<List<ExamResult>> = flow {
        val path = "exams/$examId/results"
        emitAll(
            db.collection("exams").document(examId).collection("results")
                .whereEqualTo("examOwnerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExamResult::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun unlockStudentResult(examId: String, resultId: String): Result<Unit> {
        val path = "exams/$examId/results/$resultId"
        return try {
            requireUserId()
            db.collection("exams").document(examId)
                .collection("results").document(resultId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STUDENT EXAM FLOW ---

    suspend fun loadActiveExamForStudent(examId: String): Result<Pair<Exam, List<Question>>> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            val cleanId = examId.trim()
            require(cleanId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                "Format kode ujian tidak valid."
            }
            val examSnap = db.collection("exams").document(cleanId).get().await()
            if (!examSnap.exists()) {
                return Result.failure(IllegalStateException("Paket ujian tidak ditemukan."))
            }
            val exam = examSnap.toObject(
                Exam::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Data ujian tidak dapat dibaca."))

            if (!exam.active) {
                return Result.failure(IllegalStateException("Ujian '${exam.judul}' sedang nonaktif. Hubungi pengawas."))
            }

            val questionsSnap = db.collection("exams").document(cleanId)
                .collection("questions")
                .whereEqualTo("examActive", true)
                .get()
                .await()
            val questions = questionsSnap.toObjects(
                Question::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedBy { it.orderIndex }

            if (questions.isEmpty()) {
                return Result.failure(IllegalStateException("Soal ujian belum tersedia pada paket ini."))
            }

            Result.success(exam to questions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun checkStudentAlreadySubmitted(examId: String, nama: String, kelas: String): Boolean {
        val resultId = buildStudentResultId(nama, kelas)
        val path = "exams/$examId/results/$resultId"
        return try {
            requireUserId()
            val snap = db.collection("exams").document(examId)
                .collection("results").document(resultId)
                .get()
                .await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            // If another student UID already created this deterministic resultId, GET is denied by owner/studentUid rule.
            // Treat PERMISSION_DENIED on deterministic studentKey ID as already locked!
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                true
            } else {
                handleFirestoreError(e, OperationType.GET, path)
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun submitStudentExamResult(
        exam: Exam,
        nama: String,
        kelas: String,
        skor: Int,
        benarCount: Int,
        totalCount: Int
    ): Result<ExamResult> {
        val cleanNama = nama.trim().take(100)
        val cleanKelas = kelas.trim().take(60)
        val studentKey = "${cleanNama.lowercase()}|${cleanKelas.lowercase()}"
        val resultId = buildStudentResultId(cleanNama, cleanKelas)
        val path = "exams/${exam.id}/results/$resultId"

        return try {
            val uid = requireUserId()
            require(cleanNama.isNotBlank()) { "Nama lengkap wajib diisi." }
            require(cleanKelas.isNotBlank()) { "Kelas / NIM wajib diisi." }

            val waktuSelesai = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val payload = mapOf(
                "examOwnerId" to exam.ownerId,
                "studentUid" to uid,
                "nama" to cleanNama,
                "kelas" to cleanKelas,
                "studentKey" to studentKey,
                "skor" to skor.coerceIn(0, 100),
                "benarCount" to benarCount.coerceIn(0, totalCount),
                "totalCount" to totalCount.coerceAtLeast(1),
                "waktuSelesai" to waktuSelesai,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("exams").document(exam.id)
                .collection("results").document(resultId)
                .set(payload)
                .await()

            Result.success(
                ExamResult(
                    id = resultId,
                    examOwnerId = exam.ownerId,
                    studentUid = uid,
                    nama = cleanNama,
                    kelas = cleanKelas,
                    studentKey = studentKey,
                    skor = skor.toLong(),
                    benarCount = benarCount.toLong(),
                    totalCount = totalCount.toLong(),
                    waktuSelesai = waktuSelesai
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.failure(
                    DuplicateSubmissionException(
                        "Siswa atas nama $cleanNama ($cleanKelas) sudah menyelesaikan ujian ini dan data telah dikunci."
                    )
                )
            } else {
                handleFirestoreError(e, OperationType.CREATE, path)
                Result.failure(e)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- BACKUP & RESTORE (JSON PORTABLE SNAPSHOT) ---

    suspend fun exportBackupJson(userId: String): Result<String> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi akun berubah." }

            val profileSnap = db.collection("users").document(uid).get().await()
            val profile = if (profileSnap.exists()) {
                profileSnap.toObject(SchoolProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                SchoolProfile(userId = uid)
            } ?: SchoolProfile(userId = uid)

            val examDocs = db.collection("exams").whereEqualTo("ownerId", uid).get().await().documents
            val examsArray = JSONArray()

            for (examDoc in examDocs) {
                val exam = examDoc.toObject(Exam::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: continue
                val qDocs = examDoc.reference.collection("questions").whereEqualTo("ownerId", uid).get().await()
                    .toObjects(Question::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.orderIndex }
                val rDocs = examDoc.reference.collection("results").whereEqualTo("examOwnerId", uid).get().await()
                    .toObjects(ExamResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

                val qArr = JSONArray()
                qDocs.forEach { q ->
                    qArr.put(
                        JSONObject().apply {
                            put("orderIndex", q.orderIndex)
                            put("pertanyaan", q.pertanyaan)
                            put("opsiA", q.opsiA)
                            put("opsiB", q.opsiB)
                            put("opsiC", q.opsiC)
                            put("opsiD", q.opsiD)
                            put("jawabanBenar", q.jawabanBenar)
                        }
                    )
                }

                val rArr = JSONArray()
                rDocs.forEach { r ->
                    rArr.put(
                        JSONObject().apply {
                            put("nama", r.nama)
                            put("kelas", r.kelas)
                            put("skor", r.skor)
                            put("benarCount", r.benarCount)
                            put("totalCount", r.totalCount)
                            put("waktuSelesai", r.waktuSelesai)
                        }
                    )
                }

                examsArray.put(
                    JSONObject().apply {
                        put("judul", exam.judul)
                        put("mapel", exam.mapel)
                        put("durasiMenit", exam.durasiMenit)
                        put("active", exam.active)
                        put("tokenSusulan", exam.tokenSusulan)
                        put("questions", qArr)
                        put("results", rArr)
                    }
                )
            }

            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                put(
                    "schoolProfile",
                    JSONObject().apply {
                        put("namaDinas", profile.namaDinas)
                        put("namaSekolah", profile.namaSekolah)
                        put("alamatSekolah", profile.alamatSekolah)
                        put("namaGuru", profile.namaGuru)
                    }
                )
                put("exams", examsArray)
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "exams")
            Result.failure(e)
        }
    }

    suspend fun restoreBackupJson(jsonContent: String): Result<Int> {
        return try {
            val uid = requireUserId()
            val root = JSONObject(jsonContent)

            root.optJSONObject("schoolProfile")?.let { p ->
                saveSchoolProfile(
                    namaDinas = p.optString("namaDinas", "DINAS PENDIDIKAN").ifBlank { "DINAS PENDIDIKAN" },
                    namaSekolah = p.optString("namaSekolah", "SEKOLAH").ifBlank { "SEKOLAH" },
                    alamatSekolah = p.optString("alamatSekolah", "-").ifBlank { "-" },
                    namaGuru = p.optString("namaGuru", "")
                )
            }

            val examsArr = root.optJSONArray("exams") ?: JSONArray()
            var restoredCount = 0

            for (i in 0 until examsArr.length()) {
                val exObj = examsArr.getJSONObject(i)
                val judul = exObj.optString("judul", "Ujian Restore").trim().ifBlank { "Ujian Restore" }.take(150)
                val mapel = exObj.optString("mapel", "-").trim().ifBlank { "-" }.take(100)
                val durasi = exObj.optInt("durasiMenit", 45).coerceIn(1, 300)
                val tokenSusulan = exObj.optString("tokenSusulan", "").trim().take(64)

                val qArr = exObj.optJSONArray("questions") ?: JSONArray()
                val drafts = mutableListOf<GeneratedQuestionDraft>()
                for (qIdx in 0 until qArr.length()) {
                    val qo = qArr.getJSONObject(qIdx)
                    drafts.add(
                        GeneratedQuestionDraft(
                            pertanyaan = qo.optString("pertanyaan", "Soal ${qIdx + 1}"),
                            opsiA = qo.optString("opsiA", "A. -"),
                            opsiB = qo.optString("opsiB", "B. -"),
                            opsiC = qo.optString("opsiC", "C. -"),
                            opsiD = qo.optString("opsiD", "D. -"),
                            jawabanBenar = qo.optString("jawabanBenar", "A. -")
                        )
                    )
                }

                val createRes = createExamWithQuestions(
                    judul = judul,
                    mapel = mapel,
                    durasiMenit = durasi,
                    questions = drafts,
                    setAsActive = false
                )
                val newExamId = createRes.getOrThrow()
                if (tokenSusulan.isNotBlank()) {
                    updateSusulanToken(newExamId, tokenSusulan)
                }

                val rArr = exObj.optJSONArray("results") ?: JSONArray()
                for (rIdx in 0 until rArr.length()) {
                    val ro = rArr.getJSONObject(rIdx)
                    val rNama = ro.optString("nama", "").trim().take(100)
                    val rKelas = ro.optString("kelas", "").trim().take(60)
                    if (rNama.isNotBlank() && rKelas.isNotBlank()) {
                        val rId = buildStudentResultId(rNama, rKelas)
                        val rKey = "${rNama.lowercase()}|${rKelas.lowercase()}"
                        val totalCount = ro.optInt("totalCount", drafts.size.coerceAtLeast(1)).coerceAtLeast(1)
                        val benarCount = ro.optInt("benarCount", 0).coerceIn(0, totalCount)
                        val skor = ro.optInt("skor", 0).coerceIn(0, 100)
                        val waktu = ro.optString("waktuSelesai", "-").ifBlank { "-" }.take(40)
                        db.collection("exams").document(newExamId)
                            .collection("results").document(rId)
                            .set(
                                mapOf(
                                    "examOwnerId" to uid,
                                    "studentUid" to uid,
                                    "nama" to rNama,
                                    "kelas" to rKelas,
                                    "studentKey" to rKey,
                                    "skor" to skor,
                                    "benarCount" to benarCount,
                                    "totalCount" to totalCount,
                                    "waktuSelesai" to waktu,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                    }
                }
                restoredCount++
            }
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "exams")
            Result.failure(e)
        }
    }

    companion object {
        fun buildStudentResultId(nama: String, kelas: String): String {
            val normalized = "${nama.trim().lowercase()}|${kelas.trim().lowercase()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "res_${hex.take(24)}"
        }
    }
}
