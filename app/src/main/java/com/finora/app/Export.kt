package com.finora.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.OutputStream
import java.time.*
import kotlinx.coroutines.*

private fun csvField(s: String): String {
    val safe =
        if (
            s.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                s.startsWith('\t') ||
                s.startsWith('\r')
        )
            "'" + s
        else s
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

fun exportCsv(rows: List<Entry>, out: OutputStream) {
    out.bufferedWriter().use { w ->
        w.write("Date,Description,Direction,Amount INR,Category,Source,Bank,Account\r\n")
        for (e in rows) {
            val date = Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toString()
            w.write(
                listOf(
                        date,
                        e.title,
                        if (e.credit) "Credit" else "Debit",
                        java.math.BigDecimal.valueOf(e.amount, 2).toPlainString(),
                        e.category,
                        e.source,
                        e.bank,
                        e.account,
                    )
                    .joinToString(",", transform = ::csvField) + "\r\n"
            )
        }
    }
}

fun exportPdf(rows: List<Entry>, out: OutputStream) {
    val doc = PdfDocument()
    try {
        rows
            .chunked(28)
            .ifEmpty { listOf(emptyList()) }
            .forEachIndexed { i, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
                val pen = Paint(Paint.ANTI_ALIAS_FLAG)
                pen.textSize = 20f
                page.canvas.drawText("FINORA · Financial report", 30f, 48f, pen)
                pen.textSize = 11f
                page.canvas.drawText(
                    "Recorded expenses " +
                        money(rows.spent()) +
                        "   |   " +
                        rows.size +
                        " transactions",
                    30f,
                    72f,
                    pen,
                )
                chunk.forEachIndexed { j, e ->
                    val y = 104f + j * 24
                    page.canvas.drawText(
                        e.date().toString() +
                            "   " +
                            e.title.take(30) +
                            "   ·   " +
                            e.category.take(15),
                        30f,
                        y,
                        pen,
                    )
                    pen.textAlign = Paint.Align.RIGHT
                    page.canvas.drawText(
                        (if (e.credit) "+" else "−") + money(e.amount),
                        562f,
                        y,
                        pen,
                    )
                    pen.textAlign = Paint.Align.LEFT
                }
                page.canvas.drawText("Page " + (i + 1), 30f, 810f, pen)
                doc.finishPage(page)
            }
        doc.writeTo(out)
    } finally {
        doc.close()
    }
}

@Composable
fun Export(rows: List<Entry>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    val csv =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            if (uri != null)
                scope.launch {
                    message =
                        runCatching {
                                withContext(Dispatchers.IO) {
                                    requireNotNull(ctx.contentResolver.openOutputStream(uri)).use {
                                        exportCsv(rows, it)
                                    }
                                }
                                "CSV saved"
                            }
                            .getOrElse { it.localizedMessage.orEmpty() }
                }
        }
    val pdf =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    message =
                        runCatching {
                                withContext(Dispatchers.IO) {
                                    requireNotNull(ctx.contentResolver.openOutputStream(uri)).use {
                                        exportPdf(rows, it)
                                    }
                                }
                                "PDF saved"
                            }
                            .getOrElse { it.localizedMessage.orEmpty() }
                }
        }
    OutlinedButton({ csv.launch("FINORA-transactions.csv") }) { Text("Export CSV") }
    OutlinedButton({ pdf.launch("FINORA-report.pdf") }) { Text("Export PDF") }
    if (message.isNotEmpty()) Text(message)
}
