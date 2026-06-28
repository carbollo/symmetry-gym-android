package com.aesthetic.gym.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Extracts plain text from a PDF chosen by the user (via the Storage Access Framework). */
object PdfTextExtractor {

    fun extract(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                return stripper.getText(document)
            }
        }
        throw IllegalStateException("No se pudo abrir el PDF seleccionado")
    }

    /** Best-effort display name of the picked document, used as a fallback routine name. */
    fun displayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.substringBeforeLast('.')?.trim().orEmpty()
                } else ""
            }.orEmpty().ifBlank { "Rutina importada" }
        } catch (e: Exception) {
            "Rutina importada"
        }
    }
}
