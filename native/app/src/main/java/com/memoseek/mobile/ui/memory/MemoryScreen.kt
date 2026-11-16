package com.memoseek.mobile.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import androidx.compose.ui.unit.sp
import com.memoseek.mobile.data.model.Memory
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 记忆管理。
 *
 * 只列内容与强度，不展示算法细节——用户关心的是「它记住了什么」
 * 和「不想让它记的能不能删掉」。
 */
@Composable
fun MemoryScreen(
    memories: List<Memory>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addOpen by remember { mutableStateOf(false) }
    val now = remember(memories) { System.currentTimeMillis() }
    val sorted = remember(memories, now) { memories.sortedByDescending { it.effectiveWeight(now) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(DsDimen.TopBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = DsText.s.back,
                    tint = DsColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = DsText.s.memory,
                style = DsTypography.titleMedium,
                color = DsColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { addOpen = true },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = DsText.s.addMemory,
                    tint = DsColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (sorted.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DsText.s.memoryEmpty,
                        style = DsTypography.bodyLarge,
                        color = DsColors.TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = DsText.s.memoryEmptyHint,
                        style = DsTypography.bodySmall,
                        color = DsColors.TextTertiary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = DsDimen.ContentPadding,
                    end = DsDimen.ContentPadding,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sorted, key = { it.id }) { memory ->
                    MemoryRow(
                        memory = memory,
                        weight = memory.effectiveWeight(now),
                        onDelete = { onDelete(memory.id) },
                    )
                }
            }
        }
    }

    if (addOpen) {
        AddMemoryDialog(
            onDismiss = { addOpen = false },
            onConfirm = { content ->
                onAdd(content)
                addOpen = false
            },
        )
    }
}

@Composable
private fun MemoryRow(memory: Memory, weight: Float, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DsColors.Surface)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.content,
                style = DsTypography.bodyLarge.copy(fontSize = 15.sp),
                color = DsColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 强度条：宽度直接映射权重，比数字更直观
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .fillMaxWidth(weight.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(DsColors.Brand.copy(alpha = 0.75f)),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${(weight * 100).toInt()}%",
                    style = DsTypography.bodySmall.copy(fontSize = 11.sp),
                    color = DsColors.TextCaption,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = DsText.s.delete,
                tint = DsColors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddMemoryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DsColors.Surface,
        title = {
            Text(DsText.s.addMemory, style = DsTypography.titleMedium, color = DsColors.TextPrimary)
        },
        text = {
            Column {
                Text(
                    text = DsText.s.addMemoryHint,
                    style = DsTypography.bodySmall,
                    color = DsColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = DsTypography.bodyLarge.copy(color = DsColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    placeholder = {
                        Text(
                            text = DsText.s.memoryInputPlaceholder,
                            style = DsTypography.bodySmall,
                            color = DsColors.TextCaption,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(DsText.s.save, color = if (value.isNotBlank()) DsColors.Brand else DsColors.TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DsText.s.cancel, color = DsColors.TextSecondary)
            }
        },
    )
}
