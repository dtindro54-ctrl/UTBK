package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Exam
import com.example.data.model.ExamResult
import com.example.data.model.SchoolProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExportUtil {

    fun buildCsvContent(results: List<ExamResult>): String {
        val sb = StringBuilder()
        sb.appendLine("No,Nama Siswa,Kelas,Waktu Selesai,Nilai")
        results.forEachIndexed { index, r ->
            sb.append(index + 1).append(',')
            sb.append(escapeCsv(r.nama)).append(',')
            sb.append(escapeCsv(r.kelas)).append(',')
            sb.append(escapeCsv(r.waktuSelesai)).append(',')
            sb.appendLine(r.skor)
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuote) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun printOfficialScoreReport(
        context: Context,
        profile: SchoolProfile,
        exam: Exam,
        results: List<ExamResult>
    ) {
        val html = buildOfficialHtmlReport(profile, exam, results)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val jobName = "Daftar_Nilai_${exam.judul.replace(" ", "_")}"
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                printManager.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildOfficialHtmlReport(
        profile: SchoolProfile,
        exam: Exam,
        results: List<ExamResult>
    ): String {
        val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
        val rowsHtml = if (results.isEmpty()) {
            """<tr><td colspan="5" style="text-align:center; padding:14px;">Belum ada siswa yang mengumpulkan jawaban.</td></tr>"""
        } else {
            results.mapIndexed { idx, r ->
                """
                <tr>
                    <td style="text-align:center;">${idx + 1}</td>
                    <td>${TextUtils.htmlEncode(r.nama)}</td>
                    <td>${TextUtils.htmlEncode(r.kelas)}</td>
                    <td>${TextUtils.htmlEncode(r.waktuSelesai)}</td>
                    <td style="text-align:center;"><b>${r.skor}</b></td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val teacherSignName = profile.namaGuru.ifBlank { "( ........................................ )" }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; color: #111; margin: 24px; position: relative; }
                .watermark {
                    position: fixed;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    opacity: 0.08;
                    z-index: 0;
                    width: 300px;
                    height: 300px;
                }
                .content { position: relative; z-index: 2; }
                .kop { display: flex; align-items: center; border-bottom: 3px double #000; padding-bottom: 12px; margin-bottom: 18px; }
                .kop-logo { width: 68px; height: 68px; margin-right: 16px; }
                .kop-text { flex: 1; text-align: center; }
                .kop-text p { margin: 2px 0; }
                .kop-text h2 { margin: 3px 0; font-size: 20px; letter-spacing: 0.5px; }
                .title-block { text-align: center; margin-bottom: 16px; }
                .title-block h3 { margin: 0; text-decoration: underline; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th, td { border: 1px solid #333; padding: 8px 10px; font-size: 13px; }
                th { background: #e3f2fd; text-align: left; }
                .signature { margin-top: 44px; display: flex; justify-content: flex-end; }
                .sig-box { text-align: center; width: 240px; }
                .sig-line { margin-top: 64px; border-top: 1px solid #000; padding-top: 5px; font-weight: bold; }
            </style>
        </head>
        <body>
            <svg class="watermark" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
                <path fill="#0F2B5B" d="M54,10 L90,24 L90,56 C90,78 74,95 54,102 C34,95 18,78 18,56 L18,24 Z"/>
                <path fill="#FFFFFF" d="M48,70 L34,56 L40,50 L48,58 L68,38 L74,44 Z"/>
            </svg>
            <div class="content">
                <div class="kop">
                    <svg class="kop-logo" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
                        <path fill="#0F2B5B" d="M54,10 L90,24 L90,56 C90,78 74,95 54,102 C34,95 18,78 18,56 L18,24 Z"/>
                        <path fill="#F59E0B" d="M48,70 L34,56 L40,50 L48,58 L68,38 L74,44 Z"/>
                    </svg>
                    <div class="kop-text">
                        <p style="font-size:13px; font-weight:bold;">${TextUtils.htmlEncode(profile.namaDinas)}</p>
                        <h2>${TextUtils.htmlEncode(profile.namaSekolah)}</h2>
                        <p style="font-size:12px;">${TextUtils.htmlEncode(profile.alamatSekolah)}</p>
                    </div>
                </div>
                <div class="title-block">
                    <h3>DAFTAR NILAI HASIL UJIAN</h3>
                    <p style="margin:6px 0;"><b>${TextUtils.htmlEncode(exam.judul)}</b> | Mata Pelajaran: <b>${TextUtils.htmlEncode(exam.mapel)}</b></p>
                    <p style="margin:0; font-size:12px;">Tanggal Cetak: $dateStr</p>
                </div>
                <table>
                    <tr>
                        <th style="width:42px; text-align:center;">No</th>
                        <th>Nama Lengkap Siswa</th>
                        <th>Kelas / NIM</th>
                        <th>Waktu Selesai</th>
                        <th style="width:72px; text-align:center;">Nilai</th>
                    </tr>
                    $rowsHtml
                </table>
                <div class="signature">
                    <div class="sig-box">
                        <p>Guru Mata Pelajaran,</p>
                        <div class="sig-line">${TextUtils.htmlEncode(teacherSignName)}</div>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
