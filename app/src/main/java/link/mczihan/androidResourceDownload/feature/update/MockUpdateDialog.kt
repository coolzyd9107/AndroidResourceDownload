package link.mczihan.androidResourceDownload.feature.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MockUpdateDialog(
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    forceUpdate: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (!forceUpdate) onDismiss() },
        title = { Text("发现新版本") },
        text = {
            Text(
                "当前版本：1.0.0\n" +
                    "最新版本：1.1.0\n\n" +
                    "更新内容\n" +
                    "- 优化文件列表性能\n" +
                    "- 改进下载任务恢复\n" +
                    "- 修复已知问题" +
                    if (forceUpdate) "\n\n此版本需要更新后继续使用。" else "",
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("立即更新")
            }
        },
        dismissButton = if (forceUpdate) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        },
    )
}
