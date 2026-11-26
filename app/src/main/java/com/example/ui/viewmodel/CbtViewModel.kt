package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Exam
import com.example.data.model.ExamResult
import com.example.data.model.GeneratedQuestionDraft
import com.example.data.model.Question
import com.example.data.model.SchoolProfile
import com.example.data.model.UiState
import com.example.data.repository.AiQuestionGenerator
import com.example.data.repository.CbtRepository
import com.example.data.repository.DuplicateSubmissionException
import com.example.util.LocalPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "CbtViewModel"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface AiGenState {
    data object Idle : AiGenState
    data object Generating : AiGenState
    data class Success(val examId: String, val questionCount: Int) : AiGenState
    data class Error(val message: String) : AiGenState
}

sealed interface StudentSessionState {
    data object Idle : StudentSessionState
    data object Loading : StudentSessionState
    data class TokenRequired(
        val exam: Exam,
        val questions: List<Question>,
        val errorMessage: String? = null
    ) : StudentSessionState

    data class IdentityPrompt(
        val exam: Exam,
        val questions: List<Question>,
        val checkingLock: Boolean = false,
        val errorMessage: String? = null
    ) : StudentSessionState

    data class Blocked(
        val examTitle: String,
        val message: String
    ) : StudentSessionState

    data class InProgress(
        val exam: Exam,
        val shuffledQuestions: List<Question>,
        val nama: String,
        val kelas: String,
        val lockedAnswers: Map<String, String> = emptyMap(),
        val remainingSeconds: Int
    ) : StudentSessionState

    data object Submitting : StudentSessionState
    data class Finished(
        val exam: Exam,
        val result: ExamResult
    ) : StudentSessionState

    data class Error(val message: String) : StudentSessionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class CbtViewModel(
    private val repository: CbtRepository,
    private val aiGenerator: AiQuestionGenerator,
    private val localPrefs: LocalPreferences,
    val currentUserId: String
) : ViewModel() {

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    val schoolProfileState: StateFlow<UiState<SchoolProfile>> =
        repository.observeSchoolProfile(currentUserId)
            .map<SchoolProfile, UiState<SchoolProfile>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing school profile", root)
                emit(UiState.Error(root.message ?: "Gagal memuat profil sekolah."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val teacherExamsState: StateFlow<UiState<List<Exam>>> =
        repository.observeTeacherExams(currentUserId)
            .map<List<Exam>, UiState<List<Exam>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing teacher exams", root)
                emit(UiState.Error(root.message ?: "Gagal memuat daftar ujian."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _selectedExamId = MutableStateFlow<String?>(null)
    val selectedExamId: StateFlow<String?> = _selectedExamId.asStateFlow()

    val selectedExamQuestionsState: StateFlow<UiState<List<Question>>> =
        _selectedExamId.flatMapLatest { examId ->
            if (examId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeTeacherQuestions(examId, currentUserId)
                    .map<List<Question>, UiState<List<Question>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing questions", root)
                        emit(UiState.Error(root.message ?: "Gagal memuat soal ujian."))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val selectedExamResultsState: StateFlow<UiState<List<ExamResult>>> =
        _selectedExamId.flatMapLatest { examId ->
            if (examId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeTeacherResults(examId, currentUserId)
                    .map<List<ExamResult>, UiState<List<ExamResult>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing results", root)
                        emit(UiState.Error(root.message ?: "Gagal memuat nilai siswa."))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val lastServerUrl: StateFlow<String> = localPrefs.lastServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    val teacherPin: StateFlow<String> = localPrefs.teacherPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    private val _aiGenState = MutableStateFlow<AiGenState>(AiGenState.Idle)
    val aiGenState: StateFlow<AiGenState> = _aiGenState.asStateFlow()

    private val _studentSession = MutableStateFlow<StudentSessionState>(StudentSessionState.Idle)
    val studentSession: StateFlow<StudentSessionState> = _studentSession.asStateFlow()

    private val _busyAction = MutableStateFlow(false)
    val busyAction: StateFlow<Boolean> = _busyAction.asStateFlow()

    private var timerJob: Job? = null

    fun selectExam(examId: String?) {
        _selectedExamId.value = examId
    }

    fun resetAiState() {
        _aiGenState.value = AiGenState.Idle
    }

    fun saveLastServerUrl(url: String) {
        viewModelScope.launch { localPrefs.saveLastServerUrl(url) }
    }

    fun saveTeacherPin(pin: String) {
        viewModelScope.launch {
            localPrefs.saveTeacherPin(pin)
            _toastEvents.tryEmit(if (pin.isBlank()) "PIN pengawas dinonaktifkan." else "PIN pengawas disimpan.")
        }
    }

    // --- TEACHER ACTIONS ---

    fun saveSchoolProfile(
        namaDinas: String,
        namaSekolah: String,
        alamatSekolah: String,
        namaGuru: String
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.saveSchoolProfile(namaDinas, namaSekolah, alamatSekolah, namaGuru)
            _busyAction.value = false
            res.onSuccess { _toastEvents.tryEmit("Identitas kop surat sekolah berhasil disimpan.") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menyimpan profil sekolah.") }
        }
    }

    fun generateExamWithAi(
        judul: String,
        mapel: String,
        durasiMenit: Int,
        jumlahSoal: Int,
        pdfBytes: ByteArray?,
        rawMaterialText: String
    ) {
        viewModelScope.launch {
            _aiGenState.value = AiGenState.Generating
            val aiRes = aiGenerator.generateQuestionsFromPdfOrText(
                judul = judul,
                mapel = mapel,
                jumlah = jumlahSoal,
                pdfBytes = pdfBytes,
                rawMaterialText = rawMaterialText
            )
            aiRes.fold(
                onSuccess = { drafts ->
                    val saveRes = repository.createExamWithQuestions(
                        judul = judul,
                        mapel = mapel,
                        durasiMenit = durasiMenit,
                        questions = drafts,
                        setAsActive = true
                    )
                    saveRes.fold(
                        onSuccess = { examId ->
                            _selectedExamId.value = examId
                            _aiGenState.value = AiGenState.Success(examId, drafts.size)
                            _toastEvents.tryEmit("Berhasil membuat ${drafts.size} soal & mengaktifkan ujian!")
                        },
                        onFailure = { err ->
                            _aiGenState.value = AiGenState.Error(
                                err.localizedMessage ?: "Soal dihasilkan AI, tetapi gagal menyimpan ke database."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _aiGenState.value = AiGenState.Error(
                        err.localizedMessage ?: "Gagal membuat soal menggunakan Gemini AI."
                    )
                }
            )
        }
    }

    fun createManualExam(
        judul: String,
        mapel: String,
        durasiMenit: Int,
        firstQuestion: GeneratedQuestionDraft?,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val list = if (firstQuestion != null) listOf(firstQuestion) else emptyList()
            val res = repository.createExamWithQuestions(
                judul = judul,
                mapel = mapel,
                durasiMenit = durasiMenit,
                questions = list,
                setAsActive = true
            )
            _busyAction.value = false
            res.onSuccess { examId ->
                _selectedExamId.value = examId
                _toastEvents.tryEmit("Paket ujian '$judul' berhasil dibuat.")
                onCreated(examId)
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Gagal membuat paket ujian.")
            }
        }
    }

    fun activateExam(examId: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.activateExam(examId)
            _busyAction.value = false
            res.onSuccess { _toastEvents.tryEmit("Paket ujian diaktifkan & siap dipindai siswa.") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal mengaktifkan ujian.") }
        }
    }

    fun deleteExam(examId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.deleteExam(examId)
            _busyAction.value = false
            res.onSuccess {
                if (_selectedExamId.value == examId) _selectedExamId.value = null
                _toastEvents.tryEmit("Paket ujian dan seluruh nilainya berhasil dihapus.")
                onDeleted()
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus paket ujian.")
            }
        }
    }

    fun updateSusulanToken(examId: String, token: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.updateSusulanToken(examId, token)
            _busyAction.value = false
            res.onSuccess {
                val msg = if (token.isBlank()) {
                    "Mode Reguler aktif (tanpa token susulan)."
                } else {
                    "Mode Susulan aktif dengan token: ${token.trim()}"
                }
                _toastEvents.tryEmit(msg)
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menyimpan token susulan.")
            }
        }
    }

    fun updateQuestion(
        examId: String,
        questionId: String,
        orderIndex: Int,
        pertanyaan: String,
        opsiA: String,
        opsiB: String,
        opsiC: String,
        opsiD: String,
        jawabanBenar: String
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.updateQuestion(
                examId, questionId, orderIndex, pertanyaan, opsiA, opsiB, opsiC, opsiD, jawabanBenar
            )
            _busyAction.value = false
            res.onSuccess { _toastEvents.tryEmit("Perubahan soal disimpan.") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menyimpan soal.") }
        }
    }

    fun addManualQuestion(exam: Exam, nextOrderIndex: Int, draft: GeneratedQuestionDraft) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.addManualQuestion(exam, nextOrderIndex, draft)
            _busyAction.value = false
            res.onSuccess { _toastEvents.tryEmit("Soal baru berhasil ditambahkan.") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menambahkan soal.") }
        }
    }

    fun deleteQuestion(exam: Exam, questionId: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.deleteQuestion(exam, questionId)
            _busyAction.value = false
            res.onSuccess { _toastEvents.tryEmit("Soal berhasil dihapus.") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus soal.") }
        }
    }

    fun unlockStudentResult(examId: String, resultId: String, studentName: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.unlockStudentResult(examId, resultId)
            _busyAction.value = false
            res.onSuccess {
                _toastEvents.tryEmit("Kunci dibuka! $studentName dapat mengikuti ujian susulan.")
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Gagal membuka kunci siswa.")
            }
        }
    }

    fun exportBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.exportBackupJson(currentUserId)
            _busyAction.value = false
            res.onSuccess { json -> onReady(json) }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Gagal membuat cadangan database.") }
        }
    }

    fun restoreBackupJson(jsonContent: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.restoreBackupJson(jsonContent)
            _busyAction.value = false
            res.onSuccess { count ->
                _toastEvents.tryEmit("Restore berhasil! $count paket ujian dipulihkan ke Cloud.")
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "File cadangan rusak atau tidak valid.")
            }
        }
    }

    // --- STUDENT EXAM FLOW ---

    fun startLoadingStudentExam(examId: String) {
        stopTimer()
        viewModelScope.launch {
            _studentSession.value = StudentSessionState.Loading
            val res = repository.loadActiveExamForStudent(examId)
            res.fold(
                onSuccess = { (exam, questions) ->
                    if (exam.tokenSusulan.isNotBlank()) {
                        _studentSession.value = StudentSessionState.TokenRequired(exam, questions)
                    } else {
                        _studentSession.value = StudentSessionState.IdentityPrompt(exam, questions)
                    }
                },
                onFailure = { err ->
                    _studentSession.value = StudentSessionState.Error(
                        err.localizedMessage ?: "Gagal memuat paket ujian."
                    )
                }
            )
        }
    }

    fun verifySusulanToken(inputToken: String) {
        val current = _studentSession.value as? StudentSessionState.TokenRequired ?: return
        if (inputToken.trim() == current.exam.tokenSusulan.trim()) {
            _studentSession.value = StudentSessionState.IdentityPrompt(current.exam, current.questions)
        } else {
            _studentSession.value = current.copy(errorMessage = "Token Ujian Susulan salah! Periksa kembali dari pengawas.")
        }
    }

    fun beginStudentExam(nama: String, kelas: String) {
        val current = _studentSession.value as? StudentSessionState.IdentityPrompt ?: return
        val cleanNama = nama.trim()
        val cleanKelas = kelas.trim()
        if (cleanNama.isBlank() || cleanKelas.isBlank()) {
            _studentSession.value = current.copy(errorMessage = "Nama Lengkap dan Kelas / NIM wajib diisi.")
            return
        }

        viewModelScope.launch {
            _studentSession.value = current.copy(checkingLock = true, errorMessage = null)
            val alreadyLocked = repository.checkStudentAlreadySubmitted(current.exam.id, cleanNama, cleanKelas)
            if (alreadyLocked) {
                _studentSession.value = StudentSessionState.Blocked(
                    examTitle = current.exam.judul,
                    message = "Siswa atas nama $cleanNama ($cleanKelas) sudah menyelesaikan ujian ini dan data telah dikunci."
                )
            } else {
                val shuffled = current.questions.shuffled()
                val totalSec = (current.exam.durasiMenit.coerceIn(1L, 300L) * 60L).toInt()
                _studentSession.value = StudentSessionState.InProgress(
                    exam = current.exam,
                    shuffledQuestions = shuffled,
                    nama = cleanNama,
                    kelas = cleanKelas,
                    lockedAnswers = emptyMap(),
                    remainingSeconds = totalSec
                )
                startCountdownTimer()
            }
        }
    }

    fun lockQuestionAnswer(questionId: String, selectedOption: String) {
        val current = _studentSession.value as? StudentSessionState.InProgress ?: return
        if (current.lockedAnswers.containsKey(questionId)) return
        _studentSession.value = current.copy(
            lockedAnswers = current.lockedAnswers + (questionId to selectedOption)
        )
    }

    fun submitCurrentExam() {
        val current = _studentSession.value as? StudentSessionState.InProgress ?: return
        stopTimer()
        viewModelScope.launch {
            _studentSession.value = StudentSessionState.Submitting
            val total = current.shuffledQuestions.size.coerceAtLeast(1)
            val benar = current.shuffledQuestions.count { q ->
                current.lockedAnswers[q.id] == q.jawabanBenar
            }
            val skor = ((benar.toDouble() / total.toDouble()) * 100.0).roundToInt()

            val res = repository.submitStudentExamResult(
                exam = current.exam,
                nama = current.nama,
                kelas = current.kelas,
                skor = skor,
                benarCount = benar,
                totalCount = total
            )
            res.fold(
                onSuccess = { savedResult ->
                    _studentSession.value = StudentSessionState.Finished(current.exam, savedResult)
                },
                onFailure = { err ->
                    if (err is DuplicateSubmissionException) {
                        _studentSession.value = StudentSessionState.Blocked(
                            examTitle = current.exam.judul,
                            message = err.message ?: "Data siswa telah dikunci."
                        )
                    } else {
                        _studentSession.value = StudentSessionState.Error(
                            err.localizedMessage ?: "Gagal mengirim jawaban ke server."
                        )
                    }
                }
            )
        }
    }

    fun resetStudentSession() {
        stopTimer()
        _studentSession.value = StudentSessionState.Idle
    }

    private fun startCountdownTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _studentSession.value as? StudentSessionState.InProgress ?: break
                if (current.remainingSeconds <= 1) {
                    _studentSession.value = current.copy(remainingSeconds = 0)
                    submitCurrentExam()
                    break
                } else {
                    _studentSession.value = current.copy(remainingSeconds = current.remainingSeconds - 1)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
