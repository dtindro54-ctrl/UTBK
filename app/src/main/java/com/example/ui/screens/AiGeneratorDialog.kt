package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GeneratedQuestionDraft
import com.example.ui.components.ErrorBanner
import com.example.ui.viewmodel.AiGenState
import com.example.ui.viewmodel.CbtViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGeneratorSheet(
    viewModel: CbtViewModel,
    onDismiss: () -> Unit,
    onExamReady: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiState by viewModel.aiGenState.collectAsStateWithLifecycle()

    var modeIndex by remember { mutableIntStateOf(0) } // 0 = AI PDF/Teks, 1 = Manual Kosong
    var judul by remember { mutableStateOf("") }
    var mapel by remember { mutableStateOf("") }
    var durasiText by remember { mutableStateOf("45") }
    var jumlahText by remember { mutableStateOf("5") }
    var rawText by remember { mutableStateOf("") }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedPdfUri = uri
            var displayName = "Materi.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
            }
            selectedPdfName = displayName
            localError = null
        }
    }

    LaunchedEffect(aiState) {
        if (aiState is AiGenState.Success) {
            val id = (aiState as AiGenState.Success).examId
            viewModel.resetAiState()
            onExamReady(id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (aiState !is AiGenState.Generating) {
                viewModel.resetAiState()
                onDismiss()
            }
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buat Paket Ujian Baru",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = modeIndex == 0,
                    onClick = { modeIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) {
                    Text("AI dari PDF / Materi")
                }
                SegmentedButton(
                    selected = modeIndex == 1,
                    onClick = { modeIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("Buat Manual")
                }
            }

            OutlinedTextField(
                value = judul,
                onValueChange = { judul = it; localError = null },
                label = { Text("Judul Ujian") },
                placeholder = { Text("Contoh: Penilaian Tengah Semester Ganjil") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("exam_judul_input")
            )

            OutlinedTextField(
                value = mapel,
                onValueChange = { mapel = it; localError = null },
                label = { Text("Mata Pelajaran") },
                placeholder = { Text("Contoh: Informatika") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("exam_mapel_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = durasiText,
                    onValueChange = { durasiText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Durasi (Menit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("exam_durasi_input")
                )
                if (modeIndex == 0) {
                    OutlinedTextField(
                        value = jumlahText,
                        onValueChange = { jumlahText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Jumlah Soal (1-30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("exam_jumlah_input")
                    )
                }
            }

            if (modeIndex == 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedPdfName ?: "Belum ada file PDF / eBook dipilih",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth().testTag("pick_pdf_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (selectedPdfUri == null) "Pilih File PDF / eBook" else "Ganti File PDF")
                        }
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it; localError = null },
                    label = { Text("Atau Tempel Teks Materi (Opsional jika PDF dipilih)") },
                    placeholder = { Text("Tempel ringkasan bab/materi pelajaran di sini...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("exam_material_text_input")
                )
            }

            val errText = localError ?: (aiState as? AiGenState.Error)?.message
            if (errText != null) {
                ErrorBanner(message = errText)
            }

            Button(
                onClick = {
                    val cleanJudul = judul.trim()
                    val cleanMapel = mapel.trim()
                    val durasi = (durasiText.toIntOrNull() ?: 45).coerceIn(1, 300)
                    if (cleanJudul.isBlank() || cleanMapel.isBlank()) {
                        localError = "Judul Ujian dan Mata Pelajaran wajib diisi."
                        return@Button
                    }

                    if (modeIndex == 1) {
                        viewModel.createManualExam(
                            judul = cleanJudul,
                            mapel = cleanMapel,
                            durasiMenit = durasi,
                            firstQuestion = GeneratedQuestionDraft(
                                pertanyaan = "Contoh pertanyaan pertama untuk $cleanMapel",
                                opsiA = "A. Pilihan pertama",
                                opsiB = "B. Pilihan kedua",
                                opsiC = "C. Pilihan ketiga",
                                opsiD = "D. Pilihan keempat",
                                jawabanBenar = "A. Pilihan pertama"
                            ),
                            onCreated = onExamReady
                        )
                    } else {
                        val jumlah = (jumlahText.toIntOrNull() ?: 5).coerceIn(1, 30)
                        val pdfBytes = selectedPdfUri?.let { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            } catch (e: Exception) {
                                localError = "Gagal membaca file PDF: ${e.localizedMessage}"
                                return@Button
                            }
                        }
                        if (pdfBytes == null && rawText.isBlank()) {
                            localError = "Pilih file PDF atau isi teks materi terlebih dahulu."
                            return@Button
                        }
                        viewModel.generateExamWithAi(
                            judul = cleanJudul,
                            mapel = cleanMapel,
                            durasiMenit = durasi,
                            jumlahSoal = jumlah,
                            pdfBytes = pdfBytes,
                            rawMaterialText = rawText
                        )
                    }
                },
                enabled = aiState !is AiGenState.Generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_exam_submit_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (aiState is AiGenState.Generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Gemini AI sedang menyusun soal...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (modeIndex == 0) "Generate Soal Otomatis & Simpan"
                        else "Simpan Paket Ujian Manual"
                    )
                }
            }
        }
    }
}
