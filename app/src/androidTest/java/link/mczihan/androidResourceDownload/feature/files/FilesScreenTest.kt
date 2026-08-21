package link.mczihan.androidResourceDownload.feature.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import link.mczihan.androidResourceDownload.domain.model.Role
import org.junit.Rule
import org.junit.Test

class FilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemBackReturnsToParentDirectory() {
        composeRule.setContent {
            MaterialTheme {
                FilesScreen(
                    role = Role.USER,
                    onProfile = {},
                    onDownload = {},
                    onMessage = {},
                )
            }
        }
        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("/应用发布").assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithText("/").assertIsDisplayed()
        composeRule.onNodeWithText("应用发布").assertIsDisplayed()
    }

    @Test
    fun userDoesNotSeeCloudWriteActions() {
        setFilesScreen(Role.USER)

        composeRule.onNode(hasContentDescription("上传文件")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("新建文件夹")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("管理 应用发布")).assertDoesNotExist()
    }

    @Test
    fun adminSeesUploadRenameMoveCopyAndDeleteActions() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNode(hasContentDescription("上传文件")).assertExists()
        composeRule.onNode(hasContentDescription("新建文件夹")).assertExists()
        composeRule.onNode(hasContentDescription("管理 应用发布")).performClick()
        composeRule.onNodeWithText("重命名").assertIsDisplayed()
        composeRule.onNodeWithText("移动").assertIsDisplayed()
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun moveUsesDestinationDirectoryPickerInsteadOfPathInput() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNode(hasContentDescription("管理 应用发布")).performClick()
        composeRule.onNodeWithText("移动").performClick()

        composeRule.onNodeWithText("选择移动位置").assertIsDisplayed()
        composeRule.onNodeWithText("目标目录完整路径").assertDoesNotExist()
        composeRule.onNode(hasContentDescription("打开文件夹 归档")).performClick()
        composeRule.onNodeWithText("目标：/归档/应用发布").assertIsDisplayed()
        composeRule.onNodeWithText("移动到此处").assertIsEnabled()
    }

    @Test
    fun newDirectoryActionOpensNameInput() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNode(hasContentDescription("新建文件夹")).performClick()

        composeRule.onNodeWithText("文件夹名称").assertIsDisplayed()
        composeRule.onNodeWithText("创建").assertExists()
    }

    @Test
    fun adminCanRenameFolderWithValidatedName() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNode(hasContentDescription("管理 应用发布")).performClick()
        composeRule.onNodeWithText("重命名").performClick()

        composeRule.onNodeWithText("重命名文件夹").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("应用发布")
        composeRule.onNodeWithText("重命名").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("../新版发布")
        composeRule.onNodeWithText("文件夹名称包含无效字符").assertIsDisplayed()
        composeRule.onNodeWithText("重命名").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("新版发布")
        composeRule.onNodeWithText("重命名").assertIsEnabled()
    }

    @Test
    fun adminCanOpenRenameForFile() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNode(hasContentDescription("管理 release-notes.txt")).performClick()
        composeRule.onNodeWithText("重命名").performClick()

        composeRule.onNodeWithText("重命名文件").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("release-notes.txt")
    }

    @Test
    fun supportedTextFileShowsPreviewAndOpensContent() {
        setFilesScreen(Role.USER)

        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("release-notes.txt").performClick()
        composeRule.onNodeWithText("预览").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Android Resource Download 2.2.2", substring = true)
            .assertIsDisplayed()
        composeRule.onNode(hasContentDescription("编辑文本")).assertDoesNotExist()
    }

    @Test
    fun supportedImageFileShowsPreview() {
        setFilesScreen(Role.USER)

        composeRule.onNodeWithText("预览示例.png").performClick()
        composeRule.onNodeWithText("预览").assertIsDisplayed().performClick()

        composeRule.onNode(hasContentDescription("预览示例.png")).assertExists()
        composeRule.onNode(hasContentDescription("编辑文本")).assertDoesNotExist()
    }

    @Test
    fun unsupportedPdfHidesPreviewAction() {
        setFilesScreen(Role.USER)

        composeRule.onNodeWithText("使用说明.pdf").performClick()

        composeRule.onNodeWithText("预览").assertDoesNotExist()
        composeRule.onNodeWithText("下载").assertIsDisplayed()
    }

    @Test
    fun adminCanEditCompletePlainTextPreview() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("release-notes.txt").performClick()
        composeRule.onNodeWithText("预览").performClick()
        composeRule.onNode(hasContentDescription("编辑文本")).assertIsDisplayed().performClick()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("edited text")
        composeRule.onNode(hasContentDescription("保存编辑")).assertIsEnabled().performClick()

        composeRule.onNode(hasContentDescription("编辑文本")).assertIsDisplayed()
    }

    @Test
    fun dirtyTextDraftRequiresDiscardConfirmation() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("release-notes.txt").performClick()
        composeRule.onNodeWithText("预览").performClick()
        composeRule.onNode(hasContentDescription("编辑文本")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("unsaved text")

        composeRule.onNode(hasContentDescription("退出编辑")).performClick()

        composeRule.onNodeWithText("放弃修改？").assertIsDisplayed()
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.onNode(hasContentDescription("保存编辑")).assertIsDisplayed()
    }

    private fun setFilesScreen(role: Role) {
        composeRule.setContent {
            MaterialTheme {
                FilesScreen(
                    role = role,
                    onProfile = {},
                    onDownload = {},
                    onMessage = {},
                )
            }
        }
    }
}
