package com.memoseek.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * API Key 录入。
 *
 * 阶段 2 先做成对话框，等设置页做出来再迁移过去。
 * 输入用 Password 视觉变换，避免肩窥；值本身存在 DataStore，不落明文日志。
 */
@Composable
fun ApiKeyDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DsColors.Surface,
        title = {
            Text(
                text = DsText.s.apiKey,
                style = DsTypography.titleMedium,
                color = DsColors.TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = DsText.s.apiKeyHint,
                    style = DsTypography.bodySmall,
                    color = DsColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = DsTypography.bodyLarge.copy(color = DsColors.TextPrimary),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    placeholder = {
                        Text(
                            text = "sk-…",
                            style = DsTypography.bodyLarge,
                            color = DsColors.TextCaption,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text(text = DsText.s.save, color = DsColors.Brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = DsText.s.cancel, color = DsColors.TextSecondary)
            }
        },
    )
}
