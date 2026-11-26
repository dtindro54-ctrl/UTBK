package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CbtViewModel
import com.example.ui.viewmodel.StudentSessionState

private tailrec fun Context.findActivityForSecureFlag(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForSecureFlag()
    else -> null
}

@Composable
fun StudentExamScreen(
    examId: String,
    viewModel: CbtViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.studentSession.collectAsStateWithLifecycle()

    // Anti-cheat screenshot block (FLAG_SECURE) during exam screen
    DisposableEffect(Unit) {
        val window = context.findActivityForSecureFlag()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.resetStudentSession()
        }
    }

    LaunchedEffect(examId) {
        viewModel.startLoadingStudentExam(examId)
    }

    var showExitWarning by remember { mutableStateOf(false) }

    BackHandler {
        if (session is StudentSessionState.InProgress) {
            showExitWarning = true
        } else {
            onExit()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val st = session) {
                is StudentSessionState.Idle,
                is StudentSessionState.Loading,
                is StudentSessionState.Submitting -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (st is StudentSessionState.Submitting) {
                                    "Mengunci & mengirim jawaban..."
                                } else {
                                    "Memuat lembar ujian..."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is StudentSessionState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ErrorBanner(message = st.message)
                        Button(onClick = onExit) { Text("Kembali") }
                    }
                }

                is StudentSessionState.TokenRequired -> {
                    SusulanTokenVerificationCard(
                        examTitle = st.exam.judul,
                        errorMessage = st.errorMessage,
                        onSubmitToken = { viewModel.verifySusulanToken(it) },
                        onCancel = onExit
                    )
                }

                is StudentSessionState.IdentityPrompt -> {
                    StudentIdentityCard(
                        examTitle = st.exam.judul,
                        mapel = st.exam.mapel,
                        durasiMenit = st.exam.durasiMenit,
                        questionCount = st.questions.size,
                        checkingLock = st.checkingLock,
                        errorMessage = st.errorMessage,
                        onStart = { nama, kelas -> viewModel.beginStudentExam(nama, kelas) },
                        onCancel = onExit
                    )
                }

                is StudentSessionState.Blocked -> {
                    BlockedAccessScreen(
                        examTitle = st.examTitle,
                        message = st.message,
                        onBack = onExit
                    )
                }

                is StudentSessionState.InProgress -> {
                    ActiveExamRunnerContent(
                        state = st,
                        onLockAnswer = { qId, opt -> viewModel.lockQuestionAnswer(qId, opt) },
                        onSubmit = { viewModel.submitCurrentExam() }
                    )
                }

                is StudentSessionState.Finished -> {
                    ExamFinishedLockedScreen(
                        examTitle = st.exam.judul,
                        nama = st.result.nama,
                        kelas = st.result.kelas,
                        skor = st.result.skor,
                        benarCount = st.result.benarCount,
                        totalCount = st.result.totalCount,
                        onDone = onExit
                    )
                }
            }
        }
    }

    if (showExitWarning) {
        AlertDialog(
            onDismissRequest = { showExitWarning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Keluar dari Ujian?") },
            text = { Text("Ujian sedang berlangsung. Jika keluar sekarang tanpa mengirim, jawaban belum akan tercatat.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitWarning = false
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tetap Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitWarning = false }) {
                    Text("Lanjutkan Ujian")
                }
            }
        )
    }
}

@Composable
private fun SusulanTokenVerificationCard(
    examTitle: String,
    errorMessage: String?,
    onSubmitToken: (String) -> Unit,
    onCancel: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifikasi Ujian Susulan", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = "Ujian '$examTitle' sedang dalam mode susulan. Masukkan Token dari pengawas:",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (errorMessage != null) {
                    ErrorBanner(message = errorMessage)
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Masukkan Token Susulan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("verify_susulan_token_input")
                )
                Button(
                    onClick = { onSubmitToken(token) },
                    enabled = token.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("verify_susulan_submit_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Buka Lembar Soal")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun StudentIdentityCard(
    examTitle: String,
    mapel: String,
    durasiMenit: Long,
    questionCount: Int,
    checkingLock: Boolean,
    errorMessage: String?,
    onStart: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var kelas by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusPill(
                    text = "MODE AMAN • ANTI-SCREENSHOT AKTIF",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(examTitle, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "$mapel • $durasiMenit Menit • $questionCount Soal Acak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Perhatian: Setiap jawaban yang dipilih akan langsung dikunci dan tidak dapat diubah kembali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Lengkap") },
                    placeholder = { Text("Contoh: Ahmad Fauzi") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_nama_input")
                )

                OutlinedTextField(
                    value = kelas,
                    onValueChange = { kelas = it },
                    label = { Text("Kelas / NIM") },
                    placeholder = { Text("Contoh: XII IPA 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_kelas_input")
                )

                if (errorMessage != null) {
                    ErrorBanner(message = errorMessage)
                }

                Button(
                    onClick = { onStart(nama, kelas) },
                    enabled = !checkingLock && nama.isNotBlank() && kelas.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_exam_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (checkingLock) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memeriksa status kunci...")
                    } else {
                        Text("Mulai Mengerjakan Ujian")
                    }
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}

@Composable
private fun ActiveExamRunnerContent(
    state: StudentSessionState.InProgress,
    onLockAnswer: (String, String) -> Unit,
    onSubmit: () -> Unit
) {
    var pendingLockChoice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    val minutes = state.remainingSeconds / 60
    val seconds = state.remainingSeconds % 60
    val timerText = "Sisa Waktu: %02d:%02d".format(minutes, seconds)
    val lockedCount = state.lockedAnswers.size
    val totalCount = state.shuffledQuestions.size

    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)
    ) {
        // Sticky Countdown Timer Header (Crimson matching Flask app)
        Surface(
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timerText,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("exam_timer_text")
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = "$lockedCount / $totalCount Terkunci",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${state.exam.judul} (${state.exam.mapel})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Peserta: ${state.nama} • ${state.kelas}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Perhatian: Setiap jawaban yang dipilih akan langsung dikunci dan tidak dapat diubah kembali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            itemsIndexed(state.shuffledQuestions, key = { _, q -> q.id }) { idx, q ->
                val lockedAnswer = state.lockedAnswers[q.id]
                val isLocked = lockedAnswer != null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isLocked) 1.5.dp else 1.dp,
                            color = if (isLocked) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(18.dp)
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocked) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Soal ${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLocked) {
                                StatusPill(
                                    text = stringResource(R.string.badge_locked),
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }

                        Text(
                            text = q.pertanyaan,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        q.optionsList().forEach { option ->
                            val isSelected = lockedAnswer == option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                                        else Color.Transparent
                                    )
                                    .clickable(enabled = !isLocked) {
                                        pendingLockChoice = q.id to option
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (!isLocked) {
                                        { pendingLockChoice = q.id to option }
                                    } else null,
                                    enabled = !isLocked || isSelected
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isLocked && !isSelected) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showSubmitConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_all_answers_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirim Semua Jawaban", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Per-question Answer Lock Confirmation Dialog (Exact parity with Flask kunciJawaban())
    pendingLockChoice?.let { (qId, option) ->
        AlertDialog(
            onDismissRequest = { pendingLockChoice = null },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text("Kunci Jawaban Ini?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jawaban yang sudah dipilih akan langsung dikunci dan tidak bisa diganti kembali:")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLockAnswer(qId, option)
                        pendingLockChoice = null
                    },
                    modifier = Modifier.testTag("confirm_lock_answer_button")
                ) {
                    Text("Ya, Kunci Jawaban")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLockChoice = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Kirim & Kunci Ujian?") },
            text = {
                Text(
                    "Anda telah mengunci $lockedCount dari $totalCount soal. Setelah dikirim, nilai akan dihitung dan data ujian Anda dikunci permanen."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        onSubmit()
                    },
                    modifier = Modifier.testTag("confirm_submit_exam_button")
                ) {
                    Text("Kirim Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun BlockedAccessScreen(
    examTitle: String,
    message: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    text = "Akses Ditolak!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = examTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Kembali ke Portal")
                }
            }
        }
    }
}

@Composable
private fun ExamFinishedLockedScreen(
    examTitle: String,
    nama: String,
    kelas: String,
    skor: Long,
    benarCount: Long,
    totalCount: Long,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Ujian Selesai & Terkunci!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "$nama ($kelas) • $examTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "$skor",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("final_exam_score_text")
                )
                Text(
                    text = "Jawaban benar: $benarCount dari $totalCount soal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Jawaban dan nilai Anda sudah dikunci permanen. Silakan lapor kepada pengawas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("finish_exam_done_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Selesai & Kembali")
                }
            }
        }
    }
}
