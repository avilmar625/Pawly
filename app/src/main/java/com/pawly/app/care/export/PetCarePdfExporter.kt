package com.pawly.app.care.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Creates locally generated, user-controlled PDF reports and opens Android's
 * share sheet. No health data is uploaded by this exporter.
 */
object PetCarePdfExporter {
    fun sharePassport(context: Context, petName: String, entries: List<PassportEntry>) {
        val file = createReport(
            context = context,
            petName = petName,
            title = "Pasaporte sanitario",
            subtitle = "Historial de salud y cuidados registrados",
            sections = entries.sortedByDescending { it.date }.map { entry ->
                listOf(
                    entry.title,
                    "${entry.type} · ${entry.date}",
                    entry.detail.ifBlank { "Sin notas adicionales." }
                )
            },
            emptyMessage = "Todavía no hay registros en el pasaporte sanitario."
        )
        share(context, file, "Pasaporte sanitario de $petName")
    }

    fun shareCarePlan(context: Context, petName: String, tasks: List<CareTask>) {
        val file = createReport(
            context = context,
            petName = petName,
            title = "Plan de cuidados",
            subtitle = "Rutinas y responsabilidades",
            sections = tasks.map { task ->
                listOf(
                    task.title,
                    "${task.frequency} · Responsable: ${task.responsible.ifBlank { "Sin asignar" }}",
                    if (task.completed) "Estado: marcada como realizada." else "Estado: pendiente."
                )
            },
            emptyMessage = "Todavía no hay rutinas de cuidado registradas."
        )
        share(context, file, "Plan de cuidados de $petName")
    }

    /**
     * One shareable document with the pet's profile, complete health passport
     * and care routines. All information is read locally; nothing is uploaded.
     */
    fun createCompletePetReport(
        context: Context,
        petName: String,
        breed: String,
        age: String,
        description: String,
        entries: List<PassportEntry>,
        tasks: List<CareTask>,
        species: String = "",
        traits: List<String> = emptyList()
    ): File {
        val sections = mutableListOf<List<String>>()
        sections += listOf(
            "Ficha de la mascota",
            "Nombre: $petName",
            "Especie: " + species.ifBlank { "No indicada" },
            "Raza: " + breed.ifBlank { "No indicada" },
            "Edad: " + age.ifBlank { "No indicada" },
            "Características: " + traits.joinToString(", ").ifBlank { "No indicadas" },
            "Descripción: " + description.ifBlank { "Sin descripción registrada" }
        )
        sections += entries.sortedByDescending { it.date }.map { entry ->
            listOf(entry.title, "${entry.type} · ${entry.date}", entry.detail.ifBlank { "Sin notas adicionales." })
        }
        sections += tasks.map { task ->
            listOf(
                task.title,
                "${task.frequency} · Responsable: ${task.responsible.ifBlank { "Sin asignar" }}",
                if (task.completed) "Estado: marcada como realizada." else "Estado: pendiente."
            )
        }
        return createReport(
            context = context,
            petName = petName,
            title = "Ficha completa de la mascota",
            subtitle = "Perfil, historial sanitario y plan de cuidados",
            sections = sections,
            emptyMessage = "Aún no hay datos adicionales registrados en Salud."
        )
    }

    fun shareCompletePetReport(
        context: Context,
        petName: String,
        breed: String,
        age: String,
        description: String,
        entries: List<PassportEntry>,
        tasks: List<CareTask>,
        species: String = "",
        traits: List<String> = emptyList()
    ) {
        val file = createCompletePetReport(context, petName, breed, age, description, entries, tasks, species, traits)
        share(context, file, "Ficha completa de $petName")
    }

    private fun share(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            clipData = ClipData.newUri(context.contentResolver, chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartir informe PDF"))
    }

    private fun createReport(
        context: Context,
        petName: String,
        title: String,
        subtitle: String,
        sections: List<List<String>>,
        emptyMessage: String
    ): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 46f
        val contentWidth = (pageWidth - margin * 2).toInt()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(48, 58, 52)
            textSize = 11f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 76, 57)
            textSize = 21f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 76, 57)
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(105, 113, 108)
            textSize = 9f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(214, 222, 216)
            strokeWidth = 1f
        }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page!!.canvas
            y = margin
            if (pageNumber > 1) {
                canvas!!.drawText("PAWLY  ·  $petName", margin, y, mutedPaint)
                y += 28f
            }
        }

        fun finishPage() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun drawTextBlock(text: String, paint: TextPaint, gap: Float = 8f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(1f, 1f)
                .build()
            if (y + layout.height > pageHeight - margin) {
                finishPage()
                startPage()
            }
            canvas!!.save()
            canvas!!.translate(margin, y)
            layout.draw(canvas!!)
            canvas!!.restore()
            y += layout.height + gap
        }

        try {
            startPage()
            canvas!!.drawText("PAWLY", margin, y, headingPaint)
            canvas!!.drawText("INFORME DE MASCOTA", margin, y + 18f, mutedPaint)
            y += 52f
            drawTextBlock(title, titlePaint, 5f)
            drawTextBlock(petName, headingPaint, 3f)
            drawTextBlock(subtitle, mutedPaint, 12f)
            canvas!!.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 22f
            drawTextBlock(
                "Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "ES"))),
                mutedPaint,
                18f
            )

            if (sections.isEmpty()) {
                drawTextBlock(emptyMessage, bodyPaint, 10f)
            } else {
                sections.forEachIndexed { index, lines ->
                    if (y > pageHeight - 150f) {
                        finishPage()
                        startPage()
                    }
                    drawTextBlock("${index + 1}. ${lines.getOrElse(0) { "Registro" }}", headingPaint, 3f)
                    lines.drop(1).forEach { line -> drawTextBlock(line, bodyPaint, 4f) }
                    y += 8f
                    canvas!!.drawLine(margin, y, pageWidth - margin, y, linePaint)
                    y += 15f
                }
            }
            if (y > pageHeight - 100f) {
                finishPage()
                startPage()
            }
            y += 8f
            drawTextBlock(
                "Documento generado por Pawly a partir de los datos introducidos por el usuario. " +
                    "No sustituye a la cartilla oficial, recetas ni historia clínica veterinaria. " +
                    "Comprueba la información con tu clínica antes de utilizarla como referencia médica.",
                mutedPaint,
                0f
            )
            finishPage()

            val directory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
            val safeName = petName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "mascota" }
            val output = File(directory, "pawly-$safeName-${System.currentTimeMillis()}.pdf")
            output.outputStream().use { document.writeTo(it) }
            return output
        } finally {
            runCatching { finishPage() }
            document.close()
        }
    }
}
