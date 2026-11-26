package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Exam
import com.example.data.model.SchoolProfile
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.CbtViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    viewModel: CbtViewModel,
    onBack: () -> Unit,
    onOpenExamDetail: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val examsState by viewModel.teacherExamsState.collectAsStateWithLifecycle()
    val profileState by viewModel.schoolProfileState.collectAsStateWithLifecycle()
    val savedPin by viewModel.teacherPin.collectAsStateWithLifecycle()
    val busy by viewModel.busyAction.collectAsStateWithLifecycle()

    var showGeneratorSheet by remember { mutableStateOf(false) }
    var showSchoolDialog by remember { mutableStateOf(false) }
    var examPendingDelete by remember { mutableStateOf<Exam?>(null) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    val backupSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingBackupJson
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
            }
        }
        pendingBackupJson = null
    }

    val backupRestorer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonText.isNullOrBlank()) {
                    viewModel.restoreBackupJson(jsonText)
                }
            } catch (_: Exception) {
            }
        }
    }

    val currentProfile = (profileState as? UiState.Success)?.data ?: SchoolProfile()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel Kontrol Guru", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = currentProfile.namaSekolah,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("teacher_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSchoolDialog = true },
                        modifier = Modifier.testTag("school_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Pengaturan Kop Surat & PIN"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showGeneratorSheet = true },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                text = { Text("Buat Ujian AI") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_exam_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Backup & Restore Card (Parity with Flask app)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Cadangan Database (Bank Soal & Nilai)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Data otomatis tersinkron ke Cloud Firestore. Ekspor/impor JSON kapan saja.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.exportBackupJson { json ->
                                            pendingBackupJson = json
                                            val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                            backupSaver.launch("backup_cbt_sekolah_$ts.json")
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f).testTag("backup_db_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup JSON")
                                }
                                OutlinedButton(
                                    onClick = { backupRestorer.launch(arrayOf("application/json")) },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f).testTag("restore_db_button")
                                ) {
                                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Paket Ujian Tersimpan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                when (val state = examsState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item { ErrorBanner(message = state.message) }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Description,
                                    title = "Belum Ada Paket Ujian",
                                    description = "Unggah eBook / materi PDF atau tempel ringkasan pelajaran agar Gemini AI membuatkan soal pilihan ganda otomatis.",
                                    action = {
                                        Button(onClick = { showGeneratorSheet = true }) {
                                            Icon(Icons.Filled.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Buat Paket Ujian Pertama")
                                        }
                                    }
                                )
                            }
                        } else {
                            items(state.data, key = { it.id }) { exam ->
                                ExamPackageCard(
                                    exam = exam,
                                    busy = busy,
                                    onManage = {
                                        viewModel.selectExam(exam.id)
                                        onOpenExamDetail(exam.id)
                                    },
                                    onActivate = { viewModel.activateExam(exam.id) },
                                    onDelete = { examPendingDelete = exam }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGeneratorSheet) {
        AiGeneratorSheet(
            viewModel = viewModel,
            onDismiss = { showGeneratorSheet = false },
            onExamReady = { examId ->
                showGeneratorSheet = false
                viewModel.selectExam(examId)
                onOpenExamDetail(examId)
            }
        )
    }

    if (showSchoolDialog) {
        SchoolAndPinSettingsDialog(
            profile = currentProfile,
            currentPin = savedPin,
            onDismiss = { showSchoolDialog = false },
            onSaveProfile = { dinas, sekolah, alamat, guru ->
                viewModel.saveSchoolProfile(dinas, sekolah, alamat, guru)
            },
            onSavePin = { pin -> viewModel.saveTeacherPin(pin) }
        )
    }

    examPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { examPendingDelete = null },
            title = { Text("Hapus Paket Ujian?") },
            text = {
                Text("Paket '${target.judul}' beserta seluruh soal dan nilai siswa di dalamnya akan dihapus permanen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(target.id)
                        examPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { examPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExamPackageCard(
    exam: Exam,
    busy: Boolean,
    onManage: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("exam_card_${exam.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = if (exam.active) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (exam.active) {
                            StatusPill(
                                text = stringResource(R.string.badge_active),
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        } else {
                            StatusPill(
                                text = stringResource(R.string.badge_inactive),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exam.tokenSusulan.isNotBlank()) {
                            StatusPill(
                                text = stringResource(R.string.badge_susulan),
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exam.judul,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${exam.mapel} • ${exam.durasiMenit} Menit • ${exam.questionCount} Soal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onManage,
                    modifier = Modifier.weight(1f).testTag("manage_exam_button_${exam.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_manage))
                }
                if (!exam.active) {
                    FilledTonalButton(
                        onClick = onActivate,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).testTag("activate_exam_button_${exam.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_activate))
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolAndPinSettingsDialog(
    profile: SchoolProfile,
    currentPin: String,
    onDismiss: () -> Unit,
    onSaveProfile: (String, String, String, String) -> Unit,
    onSavePin: (String) -> Unit
) {
    var dinas by remember(profile) { mutableStateOf(profile.namaDinas) }
    var sekolah by remember(profile) { mutableStateOf(profile.namaSekolah) }
    var alamat by remember(profile) { mutableStateOf(profile.alamatSekolah) }
    var guru by remember(profile) { mutableStateOf(profile.namaGuru) }
    var pin by remember(currentPin) { mutableStateOf(currentPin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Identitas Sekolah & PIN Pengawas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dinas,
                    onValueChange = { dinas = it },
                    label = { Text("Nama Dinas / Instansi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sekolah,
                    onValueChange = { sekolah = it },
                    label = { Text("Nama Sekolah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alamat,
                    onValueChange = { alamat = it },
                    label = { Text("Alamat & Telepon Sekolah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guru,
                    onValueChange = { guru = it },
                    label = { Text("Nama Guru (Tanda Tangan Laporan)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN Pengawas (Opsional, kosongkan = nonaktif)") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProfile(dinas, sekolah, alamat, guru)
                    onSavePin(pin)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_school_settings_button")
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
