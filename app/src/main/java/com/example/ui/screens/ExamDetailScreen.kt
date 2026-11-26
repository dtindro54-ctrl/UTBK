package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Exam
import com.example.data.model.ExamResult
import com.example.data.model.GeneratedQuestionDraft
import com.example.data.model.Question
import com.example.data.model.SchoolProfile
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CbtViewModel
import com.example.util.QrCodeUtil
import com.example.util.ReportExportUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    examId: String,
    viewModel: CbtViewModel,
    onBack: () -> Unit,
    onPreviewAsStudent: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(examId) {
        viewModel.selectExam(examId)
    }

    val context = LocalContext.current
    val examsState by viewModel.teacherExamsState.collectAsStateWithLifecycle()
    val questionsState by viewModel.selectedExamQuestionsState.collectAsStateWithLifecycle()
    val resultsState by viewModel.selectedExamResultsState.collectAsStateWithLifecycle()
    val profileState by viewModel.schoolProfileState.collectAsStateWithLifecycle()
    val busy by viewModel.busyAction.collectAsStateWithLifecycle()

    val exam: Exam? = (examsState as? UiState.Success)?.data?.firstOrNull { it.id == examId }
    val profile = (profileState as? UiState.Success)?.data ?: SchoolProfile()
    val questions = (questionsState as? UiState.Success)?.data ?: emptyList()
    val results = (resultsState as? UiState.Success)?.data ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingQuestion by remember { mutableStateOf<Question?>(null) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var resultPendingUnlock by remember { mutableStateOf<ExamResult?>(null) }

    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = ReportExportUtil.buildCsvContent(results)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "File CSV daftar nilai berhasil disimpan.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = exam?.judul ?: "Detail Paket Ujian",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        if (exam != null) {
                            Text(
                                text = "${exam.mapel} • ${exam.durasiMenit} Menit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("exam_detail_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (exam == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("QR & Susulan") },
                    icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                    modifier = Modifier.testTag("tab_qr_susulan")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Nilai (${results.size})") },
                    icon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                    modifier = Modifier.testTag("tab_results")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Soal (${questions.size})") },
                    icon = { Icon(Icons.Filled.Quiz, contentDescription = null) },
                    modifier = Modifier.testTag("tab_questions")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 720.dp)
            ) {
                when (selectedTab) {
                    0 -> QrAndSusulanTab(
                        exam = exam,
                        busy = busy,
                        onActivate = { viewModel.activateExam(exam.id) },
                        onSaveSusulan = { token -> viewModel.updateSusulanToken(exam.id, token) },
                        onPreviewStudent = { onPreviewAsStudent(exam.id) }
                    )
                    1 -> ResultsReportTab(
                        profile = profile,
                        exam = exam,
                        resultsState = resultsState,
                        busy = busy,
                        onPrintPdf = {
                            ReportExportUtil.printOfficialScoreReport(context, profile, exam, results)
                        },
                        onExportCsv = {
                            val safeName = exam.judul.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            csvExporter.launch("nilai_$safeName.csv")
                        },
                        onRequestUnlock = { resultPendingUnlock = it }
                    )
                    2 -> QuestionsBankTab(
                        questionsState = questionsState,
                        busy = busy,
                        onAddQuestion = { showAddQuestionDialog = true },
                        onEditQuestion = { editingQuestion = it },
                        onDeleteQuestion = { q -> viewModel.deleteQuestion(exam, q.id) }
                    )
                }
            }
        }
    }

    editingQuestion?.let { q ->
        QuestionEditorDialog(
            title = "Edit Soal #${q.orderIndex + 1}",
            initial = GeneratedQuestionDraft(
                pertanyaan = q.pertanyaan,
                opsiA = q.opsiA,
                opsiB = q.opsiB,
                opsiC = q.opsiC,
                opsiD = q.opsiD,
                jawabanBenar = q.jawabanBenar
            ),
            onDismiss = { editingQuestion = null },
            onSave = { draft ->
                viewModel.updateQuestion(
                    examId = examId,
                    questionId = q.id,
                    orderIndex = q.orderIndex.toInt(),
                    pertanyaan = draft.pertanyaan,
                    opsiA = draft.opsiA,
                    opsiB = draft.opsiB,
                    opsiC = draft.opsiC,
                    opsiD = draft.opsiD,
                    jawabanBenar = draft.jawabanBenar
                )
                editingQuestion = null
            }
        )
    }

    if (showAddQuestionDialog && exam != null) {
        QuestionEditorDialog(
            title = "Tambah Soal Baru",
            initial = GeneratedQuestionDraft(
                pertanyaan = "",
                opsiA = "A. ",
                opsiB = "B. ",
                opsiC = "C. ",
                opsiD = "D. ",
                jawabanBenar = "A. "
            ),
            onDismiss = { showAddQuestionDialog = false },
            onSave = { draft ->
                viewModel.addManualQuestion(exam, questions.size, draft)
                showAddQuestionDialog = false
            }
        )
    }

    resultPendingUnlock?.let { res ->
        AlertDialog(
            onDismissRequest = { resultPendingUnlock = null },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
            title = { Text("Buka Kunci Siswa?") },
            text = {
                Text("Buka kunci & hapus nilai lama ${res.nama} (${res.kelas}) agar dapat mengikuti ujian susulan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockStudentResult(examId, res.id, res.nama)
                        resultPendingUnlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_unlock_student_button")
                ) {
                    Text("Buka Kunci")
                }
            },
            dismissButton = {
                TextButton(onClick = { resultPendingUnlock = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun QrAndSusulanTab(
    exam: Exam,
    busy: Boolean,
    onActivate: () -> Unit,
    onSaveSusulan: (String) -> Unit,
    onPreviewStudent: () -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember(exam.tokenSusulan) { mutableStateOf(exam.tokenSusulan) }
    val encryptedPayload = remember(exam.id) { QrCodeUtil.encodeCloudExamPayload(exam.id) }
    val qrBitmap = remember(encryptedPayload) { QrCodeUtil.generateQrBitmap(encryptedPayload, 600) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (exam.active) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(
                        text = "QR CODE UJIAN AKTIF",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                    Text(
                        text = exam.judul,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 4.dp
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code Ujian Terenkripsi",
                            modifier = Modifier.size(230.dp).padding(12.dp)
                        )
                    }

                    Text(
                        text = "Scan menggunakan Mode Siswa / Exam Browser resmi",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kode Ujian / Payload Terenkripsi", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = exam.id,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("EXAMSEC QR", encryptedPayload))
                                    Toast.makeText(context, "Payload EXAMSEC:: disalin!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Payload QR")
                            }
                        }
                    }

                    Button(
                        onClick = onPreviewStudent,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("preview_student_exam_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buka Lembar Ujian Siswa Sekarang")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Paket Ujian Nonaktif", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Aktifkan paket ujian ini untuk menampilkan QR Code terenkripsi dan membuka akses bagi siswa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onActivate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("detail_activate_exam_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aktifkan Paket Ujian Ini")
                    }
                }
            }
        }

        // Panel Pengaturan Ujian Susulan (Exact parity with Flask app)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Panel Pengaturan Ujian Susulan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it.take(64) },
                    label = { Text("Token Susulan (Kosongkan untuk mode reguler)") },
                    placeholder = { Text("Contoh: SUSULAN2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("susulan_token_input")
                )

                Button(
                    onClick = { onSaveSusulan(tokenInput) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_susulan_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Simpan Pengaturan Susulan")
                }

                if (exam.tokenSusulan.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mode Susulan AKTIF — Token:", style = MaterialTheme.typography.bodySmall)
                            StatusPill(
                                text = exam.tokenSusulan,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsReportTab(
    profile: SchoolProfile,
    exam: Exam,
    resultsState: UiState<List<ExamResult>>,
    busy: Boolean,
    onPrintPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onRequestUnlock: (ExamResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Official Kop Surat Preview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile.namaDinas,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = profile.namaSekolah,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = profile.alamatSekolah,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "DAFTAR NILAI HASIL UJIAN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${exam.judul} • ${exam.mapel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPrintPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.weight(1f).testTag("print_pdf_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cetak / PDF")
                        }
                        FilledTonalButton(
                            onClick = onExportCsv,
                            modifier = Modifier.weight(1f).testTag("export_csv_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Excel (CSV)")
                        }
                    }
                }
            }
        }

        when (resultsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item { ErrorBanner(message = resultsState.message) }
            }
            is UiState.Success -> {
                val list = resultsState.data
                if (list.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.TableChart,
                            title = "Belum Ada Nilai Masuk",
                            description = "Hasil ujian siswa yang telah selesai akan muncul secara real-time di sini."
                        )
                    }
                } else {
                    val avg = list.map { it.skor }.average()
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatChipCard("Peserta", "${list.size}", Modifier.weight(1f))
                            StatChipCard("Rata-rata", "%.1f".format(avg), Modifier.weight(1f))
                            StatChipCard("Tertinggi", "${list.maxOf { it.skor }}", Modifier.weight(1f))
                        }
                    }

                    itemsIndexed(list, key = { _, item -> item.id }) { index, res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${res.skor}",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${res.nama}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${res.kelas} • Benar ${res.benarCount}/${res.totalCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = res.waktuSelesai,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onRequestUnlock(res) },
                                    enabled = !busy,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("unlock_student_button_${res.id}")
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buka Kunci", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChipCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestionsBankTab(
    questionsState: UiState<List<Question>>,
    busy: Boolean,
    onAddQuestion: () -> Unit,
    onEditQuestion: (Question) -> Unit,
    onDeleteQuestion: (Question) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(
                onClick = onAddQuestion,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_manual_question_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tambah Soal Manual")
            }
        }

        when (questionsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item { ErrorBanner(message = questionsState.message) }
            }
            is UiState.Success -> {
                val list = questionsState.data
                if (list.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Quiz,
                            title = "Belum Ada Soal",
                            description = "Tambahkan soal secara manual untuk melengkapi paket ujian ini."
                        )
                    }
                } else {
                    itemsIndexed(list, key = { _, q -> q.id }) { idx, q ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = "SOAL ${idx + 1}",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        IconButton(onClick = { onEditQuestion(q) }, enabled = !busy) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Soal")
                                        }
                                        IconButton(onClick = { onDeleteQuestion(q) }, enabled = !busy) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = "Hapus Soal",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = q.pertanyaan,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                q.optionsList().forEach { opt ->
                                    val isCorrect = opt == q.jawabanBenar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCorrect) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCorrect) MaterialTheme.colorScheme.onTertiaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCorrect) {
                                            StatusPill(
                                                text = "KUNCI",
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditorDialog(
    title: String,
    initial: GeneratedQuestionDraft,
    onDismiss: () -> Unit,
    onSave: (GeneratedQuestionDraft) -> Unit
) {
    var pertanyaan by remember(initial) { mutableStateOf(initial.pertanyaan) }
    var opsiA by remember(initial) { mutableStateOf(initial.opsiA) }
    var opsiB by remember(initial) { mutableStateOf(initial.opsiB) }
    var opsiC by remember(initial) { mutableStateOf(initial.opsiC) }
    var opsiD by remember(initial) { mutableStateOf(initial.opsiD) }
    var selectedKeyIdx by remember(initial) {
        mutableIntStateOf(
            when (initial.jawabanBenar) {
                initial.opsiB -> 1
                initial.opsiC -> 2
                initial.opsiD -> 3
                else -> 0
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pertanyaan,
                    onValueChange = { pertanyaan = it },
                    label = { Text("Pertanyaan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                listOf(
                    Triple(0, "Opsi A", opsiA to { v: String -> opsiA = v }),
                    Triple(1, "Opsi B", opsiB to { v: String -> opsiB = v }),
                    Triple(2, "Opsi C", opsiC to { v: String -> opsiC = v }),
                    Triple(3, "Opsi D", opsiD to { v: String -> opsiD = v })
                ).forEach { (idx, label, pair) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedKeyIdx == idx,
                            onClick = { selectedKeyIdx = idx }
                        )
                        OutlinedTextField(
                            value = pair.first,
                            onValueChange = pair.second,
                            label = { Text("$label ${if (selectedKeyIdx == idx) "(Kunci)" else ""}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val key = listOf(opsiA, opsiB, opsiC, opsiD)[selectedKeyIdx]
                    onSave(
                        GeneratedQuestionDraft(
                            pertanyaan = pertanyaan,
                            opsiA = opsiA,
                            opsiB = opsiB,
                            opsiC = opsiC,
                            opsiD = opsiD,
                            jawabanBenar = key
                        )
                    )
                },
                enabled = pertanyaan.isNotBlank() && opsiA.isNotBlank() && opsiB.isNotBlank() && opsiC.isNotBlank() && opsiD.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
