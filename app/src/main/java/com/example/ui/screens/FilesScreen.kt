package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.ui.components.FileItemCard
import com.example.ui.theme.TelegramBlue
import com.example.ui.viewmodel.TeleStreamViewModel
import com.example.util.NetworkUtils

@Composable
fun FilesScreen(
    viewModel: TeleStreamViewModel,
    onOpenCreateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val files by viewModel.filteredFiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenCreateDialog,
                containerColor = TelegramBlue,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_stream")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Stream")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search files by name or mime type...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("search_files_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("All") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = selectedCategory == FileCategory.VIDEO,
                    onClick = { viewModel.selectCategory(FileCategory.VIDEO) },
                    label = { Text("🎬 Videos") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = selectedCategory == FileCategory.AUDIO,
                    onClick = { viewModel.selectCategory(FileCategory.AUDIO) },
                    label = { Text("🎵 Audio") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = selectedCategory == FileCategory.DOCUMENT,
                    onClick = { viewModel.selectCategory(FileCategory.DOCUMENT) },
                    label = { Text("📄 Docs") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = selectedCategory == FileCategory.IMAGE,
                    onClick = { viewModel.selectCategory(FileCategory.IMAGE) },
                    label = { Text("🖼️ Images") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = selectedCategory == FileCategory.ARCHIVE,
                    onClick = { viewModel.selectCategory(FileCategory.ARCHIVE) },
                    label = { Text("📦 Archives") },
                    shape = RoundedCornerShape(999.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subheader: Count + Clear All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSize = files.sumOf { it.fileSize }
                Text(
                    text = "${files.size} items • ${NetworkUtils.formatBytes(totalSize)} total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (files.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllFiles() }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                        Text("Clear List", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Files LazyColumn
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching files found" else "No files in stream library",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Send media to your Telegram Bot or tap the '+' button below to create download & streaming links.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.id }) { file ->
                        FileItemCard(
                            file = file,
                            onPlayOrPreview = { viewModel.setPreviewFile(file) },
                            onCopyDownloadUrl = {
                                viewModel.copyToClipboard(viewModel.getDownloadUrl(file.id), "Direct Download Link")
                            },
                            onCopyStreamUrl = {
                                viewModel.copyToClipboard(viewModel.getStreamUrl(file.id), "Stream Link")
                            },
                            onShowQr = { viewModel.setQrFile(file) },
                            onDelete = { viewModel.deleteFile(file.id) }
                        )
                    }
                }
            }
        }
    }
}
