package com.abk.kernel.miuix.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixTextInputDialog(
    show: Boolean,
    title: String,
    message: String,
    label: String,
    value: String,
    cancelText: String,
    confirmText: String,
    confirmEnabled: Boolean = true,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = cancelText,
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = confirmText,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onConfirm,
                )
            }
        }
    }
}
