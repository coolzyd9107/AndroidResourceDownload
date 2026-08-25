package com.resdownload.android.feature.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.resdownload.android.domain.model.Role
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
                    onDownload = { _, _ -> },
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

        composeRule.onNode(hasContentDescription("上传")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("新建文件夹")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("管理 应用发布")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("选择文件搜索结果")).assertDoesNotExist()
    }

    @Test
    fun topBarKeepsRefreshAndRemovesProfileEntry() {
        setFilesScreen(Role.USER)

        composeRule.onNode(hasContentDescription("刷新文件列表")).assertExists()
        composeRule.onNode(hasContentDescription("个人中心")).assertDoesNotExist()
    }

    @Test
    fun fileSearchSwitchesBetweenCurrentSubtreeAndEntireCloud() {
        setFilesScreen(Role.USER)
        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNodeWithText("当前目录").assertIsDisplayed()
        composeRule.onNodeWithText("整个云盘").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("界面")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()

        composeRule.onNodeWithText("未找到匹配的文件或文件夹").assertIsDisplayed()

        composeRule.onNodeWithText("整个云盘").performClick()

        composeRule.onNodeWithText("界面规范.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("/设计资料/界面规范.pdf", substring = true).assertExists()
        composeRule.onNodeWithText("部分目录无法访问，搜索结果可能不完整").assertIsDisplayed()
    }

    @Test
    fun directorySearchResultOpensDirectoryAndClosesSearch() {
        setFilesScreen(Role.USER)
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("设计资料")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()

        composeRule.onNode(
            hasText("设计资料", substring = true) and
                hasClickAction() and
                !hasSetTextAction(),
        ).performClick()

        composeRule.onNodeWithText("/设计资料").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("关闭文件搜索")).assertDoesNotExist()
    }

    @Test
    fun userCannotSelectFileSearchResults() {
        setFilesScreen(Role.USER)
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("pdf")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()
        composeRule.onNodeWithText("使用说明.pdf").assertExists()

        composeRule.onNode(hasContentDescription("选择文件搜索结果")).assertDoesNotExist()
        composeRule.onNodeWithText("全选").assertDoesNotExist()
    }

    @Test
    fun adminCanSelectCrossDirectoryFileSearchResults() {
        setFilesScreen(Role.ADMIN)
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("pdf")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()
        composeRule.onNode(hasContentDescription("选择文件搜索结果")).performClick()

        composeRule.onNodeWithText("全选").performClick()

        composeRule.onNodeWithText("已选择 2 项").assertExists()
        composeRule.onNodeWithText("移动").assertExists()
        composeRule.onNodeWithText("复制").assertExists()
        composeRule.onNodeWithText("下载").assertExists()
        composeRule.onNodeWithText("删除").assertExists()
    }

    @Test
    fun backFromFileSearchSelectionReturnsToSearchBeforeClosingIt() {
        setFilesScreen(Role.ADMIN)
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("pdf")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()
        composeRule.onNode(hasContentDescription("选择文件搜索结果")).performClick()
        composeRule.onNodeWithText("全选").performClick()

        Espresso.pressBack()

        composeRule.onNode(hasContentDescription("关闭文件搜索")).assertExists()
        composeRule.onNodeWithText("使用说明.pdf").assertExists()
        composeRule.onNodeWithText("已选择 2 项").assertDoesNotExist()
    }

    @Test
    fun selectedFolderSearchIncludesItsDescendantsOnly() {
        setFilesScreen(Role.ADMIN)
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("设计资料")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()
        composeRule.onNode(hasContentDescription("选择文件搜索结果")).performClick()
        composeRule.onNode(
            hasText("设计资料", substring = true) and
                hasClickAction() and
                !hasSetTextAction(),
        ).performClick()
        composeRule.onNode(hasContentDescription("在已选文件中搜索")).performClick()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("界面")
        composeRule.onNode(hasContentDescription("搜索已选文件和文件夹")).performClick()

        composeRule.onNodeWithText("界面规范.pdf").assertExists()
        composeRule.onNodeWithText("release-notes.txt").assertDoesNotExist()
        composeRule.onNodeWithText("在 1 个已选项中搜索").assertExists()
    }

    @Test
    fun submittedFileSearchRerunsAfterStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                FilesScreen(
                    role = Role.USER,
                    onDownload = { _, _ -> },
                    onMessage = {},
                )
            }
        }
        composeRule.onNode(hasContentDescription("搜索文件和文件夹")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("release-notes")
        composeRule.onNode(hasContentDescription("执行文件搜索")).performClick()
        composeRule.onNodeWithText("release-notes.txt").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("release-notes.txt").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("关闭文件搜索")).assertExists()
    }

    @Test
    fun adminSeesUploadRenameMoveCopyAndDeleteActions() {
        setFilesScreen(Role.ADMIN)

        composeRule.onNode(hasContentDescription("上传")).assertExists().performClick()
        composeRule.onNode(hasContentDescription("上传文件")).assertExists()
        composeRule.onNode(hasContentDescription("上传文件夹")).assertExists()
        composeRule.onNode(hasContentDescription("上传文件")).performClick()
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
        composeRule.onNode(hasSetTextAction() and hasText("应用发布")).assertExists()
        composeRule.onNodeWithText("重命名").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("../新版发布")
        composeRule.onNode(hasSetTextAction() and hasText("../新版发布")).assertExists()
        composeRule.onNodeWithText(
            "文件夹名称包含无效字符",
            useUnmergedTree = true,
        ).assertExists()
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
        composeRule.onNode(hasSetTextAction() and hasText("release-notes.txt")).assertExists()
    }

    @Test
    fun supportedTextFileShowsPreviewAndOpensContent() {
        setFilesScreen(Role.USER)

        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("release-notes.txt").performClick()
        composeRule.onNodeWithText("预览").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Android Resource Download 2.3.0", substring = true)
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
                    onDownload = { _, _ -> },
                    onMessage = {},
                )
            }
        }
    }
}
