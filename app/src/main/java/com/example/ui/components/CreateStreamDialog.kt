package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CreateStreamDialog(
    onDismiss: () -> Unit,
    onCreateStream: (name: String, tgFileId: String, sizeMb: Double, mime: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var fileId by remember { mutableStateOf("") }
    var sizeMbStr by remember { mutableStateOf("25") }
    var mimeType by remember { mutableStateOf("video/mp4") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "➕ Add Stream Link",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (e.g. video.mp4)") },
                    placeholder = { Text("MyStreamFile.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_stream_filename")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fileId,
                    onValueChange = { fileId = it },
                    label = { Text("Telegram File ID / Token") },
                    placeholder = { Text("BAACAgIAAxkBAA...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_stream_fileid")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = sizeMbStr,
                        onValueChange = { sizeMbStr = it },
                        label = { Text("Size (MB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = mimeType,
                        onValueChange = { mimeType = it },
                        label = { Text("MIME Type") },
                        placeholder = { Text("video/mp4") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val sizeBytes = ((sizeMbStr.toDoubleOrNull() ?: 10.0) * 1024 * 1024).toLong()
                        onCreateStream(
                            fileName.ifBlank { "manual_file_${System.currentTimeMillis()}.mp4" },
                            fileId.ifBlank { "TG_FILE_MANUAL_${System.currentTimeMillis()}" },
                            sizeMbStr.toDoubleOrNull() ?: 10.0,
                            mimeType.ifBlank { "video/mp4" }
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_create_stream_submit"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Generate Stream & Download Links", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
