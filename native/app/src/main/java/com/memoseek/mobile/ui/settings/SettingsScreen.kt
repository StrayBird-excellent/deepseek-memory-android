package com.memoseek.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.AppLanguage
import com.memoseek.mobile.ui.i18n.DsText
import androidx.compose.ui.unit.sp
import com.memoseek.mobile.data.remote.BalanceInfoDto
import com.memoseek.mobile.data.remote.BalanceResponse
import com.memoseek.mobile.data.remote.DeepSeekClient
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsTypography
import com.memoseek.mobile.data.model.AvailableModels
import com.memoseek.mobile.data.model.modelById
import com.memoseek.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * 设置页。
 *
 * 结构对齐官方：全屏页面、返回箭头、居中标题、分组卡片。
 * 分组顺序也是官方的：账户在前，应用与关于在后。
 *
 * 余额只读不代扣——充值必须在官方站点完成，客户端不接触任何支付信息。
 */
@Composable
fun SettingsScreen(
    apiKey: String,
    themeMode: ThemeMode,
    modelId: String,
    onBack: () -> Unit,
    onEditApiKey: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onModelChange: (String) -> Unit,
    onOpenMemory: () -> Unit,
    memoryCount: Int,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val client = remember { DeepSeekClient() }
    var balance by remember { mutableStateOf<BalanceResponse?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var themePickerOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) return@LaunchedEffect
        loading = true
        balanceError = null
        runCatching { client.fetchBalance(apiKey) }
            .onSuccess { balance = it }
            .onFailure { balanceError = it.message ?: DsText.s.balanceFailed }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 顶栏：返回箭头 + 居中标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DsDimen.TopBarHeight),
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
                text = DsText.s.settings,
                style = DsTypography.titleMedium,
                color = DsColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }

        if (languagePickerOpen) {
            LanguagePicker(
                current = language,
                onSelect = {
                    onLanguageChange(it)
                    languagePickerOpen = false
                },
                onDismiss = { languagePickerOpen = false },
            )
        }

        if (modelPickerOpen) {
            ModelPicker(
                currentId = modelId,
                onSelect = {
                    onModelChange(it)
                    modelPickerOpen = false
                },
                onDismiss = { modelPickerOpen = false },
            )
        }

        if (themePickerOpen) {
            ThemePicker(
                current = themeMode,
                onSelect = {
                    onThemeChange(it)
                    themePickerOpen = false
                },
                onDismiss = { themePickerOpen = false },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = DsDimen.ContentPadding,
                end = DsDimen.ContentPadding,
                bottom = 32.dp,
            ),
        ) {
            item { GroupTitle(DsText.s.groupAccount) }
            item {
                GroupCard {
                    SettingRow(
                        label = DsText.s.apiKey,
                        value = if (apiKey.isBlank()) DsText.s.apiKeyNotSet else maskKey(apiKey),
                        onClick = onEditApiKey,
                    )
                    Divider()
                    BalanceRow(balance = balance, loading = loading, error = balanceError)
                }
            }

            item { GroupTitle(DsText.s.groupApp) }
            item {
                GroupCard {
                    SettingRow(
                        label = DsText.s.appearance,
                        value = themeMode.label(),
                        onClick = { themePickerOpen = true },
                    )
                    Divider()
                    SettingRow(label = DsText.s.language, value = DsText.s.languageChinese, showChevron = false)
                    Divider()
                    SettingRow(
                        label = DsText.s.memory,
                        value = if (memoryCount > 0) String.format(DsText.s.memoryCountFormat, memoryCount) else DsText.s.memoryDisabled,
                        onClick = onOpenMemory,
                    )
                }
            }

            item { GroupTitle(DsText.s.groupAbout) }
            item {
                GroupCard {
                    SettingRow(label = DsText.s.version, value = "0.1.0", showChevron = false)
                    Divider()
                    SettingRow(
                        label = DsText.s.model,
                        value = modelById(modelId).name,
                        onClick = { modelPickerOpen = true },
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
            }
            item {
                Text(
                    text = DsText.s.paymentNote,
                    style = DsTypography.bodySmall,
                    color = DsColors.TextCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = DsTypography.bodySmall.copy(fontSize = 12.sp),
        color = DsColors.TextTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DsColors.Surface),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = DsTypography.bodyLarge, color = DsColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = DsTypography.bodySmall,
                color = DsColors.TextTertiary,
            )
            if (showChevron) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DsColors.TextTertiary,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(balance: BalanceResponse?, loading: Boolean, error: String?) {
    val primary: BalanceInfoDto? = balance?.balanceInfos?.firstOrNull()
    val value = when {
        loading -> DsText.s.loading
        error != null -> error
        primary != null -> formatMoney(primary)
        else -> "—"
    }
    SettingRow(label = DsText.s.balance, value = value, showChevron = false)
}

private fun formatMoney(info: BalanceInfoDto): String {
    val symbol = if (info.currency == "USD") "$" else "¥"
    return "$symbol${info.totalBalance}"
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(DsColors.CapsuleBorder),
    )
}

/** 只露头尾，避免整串 Key 出现在截图或投屏里 */
private fun maskKey(key: String): String = when {
    key.length <= 10 -> key
    else -> "${key.take(6)}····${key.takeLast(4)}"
}

@Composable
private fun ThemePicker(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DsColors.Surface,
        title = {
            Text(text = DsText.s.appearance, style = DsTypography.titleMedium, color = DsColors.TextPrimary)
        },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.label(),
                            style = DsTypography.bodyLarge,
                            color = if (mode == current) DsColors.Brand else DsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == current) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = DsColors.Brand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DsText.s.cancel, color = DsColors.TextSecondary)
            }
        },
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> DsText.current.themeSystem
    ThemeMode.Light -> DsText.current.themeLight
    ThemeMode.Dark -> DsText.current.themeDark
}

@Composable
private fun ModelPicker(
    currentId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DsColors.Surface,
        title = {
            Text(text = DsText.s.selectModel, style = DsTypography.titleMedium, color = DsColors.TextPrimary)
        },
        text = {
            Column {
                AvailableModels.forEach { model ->
                    val selected = model.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(model.id) }
                            .padding(horizontal = 8.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                style = DsTypography.bodyLarge,
                                color = if (selected) DsColors.Brand else DsColors.TextPrimary,
                            )
                            Text(
                                text = model.tagline,
                                style = DsTypography.bodySmall,
                                color = DsColors.TextTertiary,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = DsColors.Brand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DsText.s.cancel, color = DsColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun LanguagePicker(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DsColors.Surface,
        title = {
            Text(DsText.s.selectLanguage, style = DsTypography.titleMedium, color = DsColors.TextPrimary)
        },
        text = {
            Column {
                AppLanguage.entries.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(lang) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lang.label(),
                            style = DsTypography.bodyLarge,
                            color = if (lang == current) DsColors.Brand else DsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (lang == current) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = DsColors.Brand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DsText.s.cancel, color = DsColors.TextSecondary)
            }
        },
    )
}

private fun AppLanguage.label(): String = when (this) {
    AppLanguage.System -> DsText.current.languageSystem
    AppLanguage.Chinese -> DsText.current.languageChinese
    AppLanguage.English -> DsText.current.languageEnglish
}
