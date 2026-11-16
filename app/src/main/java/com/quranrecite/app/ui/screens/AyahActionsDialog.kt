package com.quranrecite.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.quranrecite.app.ui.theme.HafsFont
import com.quranrecite.app.ui.theme.Muted

/** Long-press menu of an ayah in the reader: copy it or save it as the bookmark. */
@Composable
fun AyahActionsDialog(
    title: String,
    ayahText: String,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, color = Muted) },
        text = {
            Text(
                ayahText, fontFamily = HafsFont, fontSize = 22.sp, lineHeight = 36.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopy) { Text("نسخ الآية") }
                TextButton(onClick = onBookmark) { Text("حفظ كعلامة") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
