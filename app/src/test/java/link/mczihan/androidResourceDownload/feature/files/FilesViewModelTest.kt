package link.mczihan.androidResourceDownload.feature.files

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun navigateUpLoadsImmediateParent() = runTest(dispatcher) {
        val requestedPaths = mutableListOf<WebDavPath>()
        val viewModel = FilesViewModel(
            repository = FakeFileRepository { path ->
                requestedPaths += path
                emptyList()
            },
        )
        runCurrent()

        viewModel.openDirectory(WebDavPath.parseDecoded("/parent/child"))
        runCurrent()
        viewModel.navigateUp()

        assertEquals("/parent", viewModel.state.value.path.toString())
        runCurrent()
        assertTrue(viewModel.state.value is FilesUiState.Empty)
        assertEquals(listOf("/", "/parent/child", "/parent"), requestedPaths.map(WebDavPath::toString))
    }

    @Test
    fun lateChildResultCannotReplaceParent() = runTest(dispatcher) {
        val childResult = CompletableDeferred<List<FileNode>>()
        val viewModel = FilesViewModel(
            repository = FakeFileRepository { path ->
                if (path.toString() == "/child") childResult.await() else emptyList()
            },
        )
        runCurrent()

        viewModel.openDirectory(WebDavPath.parseDecoded("/child"))
        runCurrent()
        viewModel.navigateUp()
        runCurrent()
        childResult.complete(
            listOf(FileNode("late.txt", "/child/late.txt", isDirectory = false)),
        )
        advanceUntilIdle()

        assertEquals("/", viewModel.state.value.path.toString())
        assertTrue(viewModel.state.value is FilesUiState.Empty)
    }

    @Test
    fun navigateUpAtRootDoesNothing() = runTest(dispatcher) {
        var requestCount = 0
        val viewModel = FilesViewModel(
            repository = FakeFileRepository {
                requestCount += 1
                emptyList()
            },
        )
        runCurrent()

        viewModel.navigateUp()
        runCurrent()

        assertEquals(1, requestCount)
        assertEquals("/", viewModel.state.value.path.toString())
    }

    @Test
    fun moveConflictWaitsForExplicitOverwriteThenRefreshes() = runTest(dispatcher) {
        val overwriteAttempts = mutableListOf<Boolean>()
        val destinations = mutableListOf<WebDavPath>()
        var listCalls = 0
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> {
                listCalls++
                return emptyList()
            }

            override suspend fun move(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                overwriteAttempts += overwrite
                destinations += destination
                if (!overwrite) throw WebDavException.PreconditionFailed()
            }

            override suspend fun isCollection(path: WebDavPath): Boolean = false
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.move(
            source = WebDavPath.parseDecoded("/source.txt"),
            sourceIsDirectory = false,
            destinationDirectory = WebDavPath.parseDecoded("/archive"),
        )
        advanceUntilIdle()
        assertTrue(viewModel.mutationState.value is FileMutationState.AwaitingOverwrite)

        viewModel.confirmOverwrite()
        advanceUntilIdle()

        assertEquals(listOf(false, true), overwriteAttempts)
        assertEquals(listOf("/archive/source.txt", "/archive/source.txt"), destinations.map(WebDavPath::toString))
        assertEquals(FileMutationState.Idle, viewModel.mutationState.value)
        assertEquals(2, listCalls)
    }

    @Test
    fun collectionConflictNeverOffersOverwrite() = runTest(dispatcher) {
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun copy(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                throw WebDavException.PreconditionFailed()
            }

            override suspend fun isCollection(path: WebDavPath): Boolean = true
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.copy(
            source = WebDavPath.parseDecoded("/source"),
            sourceIsDirectory = true,
            destinationDirectory = WebDavPath.parseDecoded("/archive"),
        )
        advanceUntilIdle()

        val failure = viewModel.mutationState.value as FileMutationState.Failed
        assertEquals(null, failure.operation)
        assertTrue(failure.message.contains("不能直接覆盖"))
    }

    @Test
    fun destinationPickerListsOnlyDirectoriesWithoutChangingMainPath() = runTest(dispatcher) {
        val viewModel = FilesViewModel(
            repository = FakeFileRepository { path ->
                if (path.toString() == "/archive") {
                    listOf(
                        FileNode("nested", "/archive/nested", isDirectory = true),
                        FileNode("notes.txt", "/archive/notes.txt", isDirectory = false),
                    )
                } else {
                    emptyList()
                }
            },
        )
        runCurrent()

        viewModel.openDestinationPicker(WebDavPath.parseDecoded("/archive"))
        advanceUntilIdle()

        assertEquals("/", viewModel.state.value.path.toString())
        val pickerState = viewModel.directoryPickerState.value as DirectoryPickerState.Success
        assertEquals("/archive", pickerState.path.toString())
        assertEquals(listOf("nested"), pickerState.directories.map(FileNode::name))
    }

    @Test
    fun createDirectoryUsesCurrentPathAndRefreshesList() = runTest(dispatcher) {
        var createdPath: WebDavPath? = null
        var listCalls = 0
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> {
                listCalls++
                return emptyList()
            }

            override suspend fun createDirectory(path: WebDavPath) {
                createdPath = path
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.createDirectory("资料")
        advanceUntilIdle()

        assertEquals("/资料", createdPath.toString())
        assertEquals(FileMutationState.Idle, viewModel.mutationState.value)
        assertEquals(2, listCalls)
    }

    @Test
    fun existingDirectoryDoesNotOfferOverwrite() = runTest(dispatcher) {
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun createDirectory(path: WebDavPath) {
                throw WebDavException.PreconditionFailed(405)
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.createDirectory("existing")
        advanceUntilIdle()

        val failure = viewModel.mutationState.value as FileMutationState.Failed
        assertEquals(null, failure.operation)
        assertTrue(failure.message.contains("同名"))
    }

    @Test
    fun previewLoadsContentAndDismissReleasesIt() = runTest(dispatcher) {
        val file = FileNode("notes.txt", "/notes.txt", isDirectory = false, mimeType = "text/plain")
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent =
                FilePreviewContent.Text("preview content")
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.preview(file)
        advanceUntilIdle()

        val content = viewModel.previewState.value as FilePreviewUiState.Content
        assertEquals(file, content.file)
        assertEquals("preview content", (content.preview as FilePreviewContent.Text).text)

        viewModel.dismissPreview()
        assertEquals(FilePreviewUiState.Idle, viewModel.previewState.value)
    }

    @Test
    fun unsupportedFileDoesNotStartPreviewRead() = runTest(dispatcher) {
        var previewCalls = 0
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent {
                previewCalls++
                return FilePreviewContent.Text("unexpected")
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.preview(
            FileNode("manual.pdf", "/manual.pdf", isDirectory = false, mimeType = "application/pdf"),
        )
        advanceUntilIdle()

        assertEquals(0, previewCalls)
        assertEquals(FilePreviewUiState.Idle, viewModel.previewState.value)
    }

    @Test
    fun completePlainTextCanBeEditedAndSaved() = runTest(dispatcher) {
        val file = FileNode(
            "notes.txt",
            "/notes.txt",
            isDirectory = false,
            mimeType = "text/plain",
            etag = "\"v1\"",
        )
        var savedText: String? = null
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent =
                FilePreviewContent.Text("old text", entityTag = "\"v1\"")

            override suspend fun updateText(
                file: FileNode,
                original: FilePreviewContent.Text,
                text: String,
            ) {
                savedText = text
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()
        viewModel.preview(file)
        advanceUntilIdle()

        viewModel.startPreviewEdit()
        viewModel.updatePreviewDraft("new text")
        val editing = viewModel.previewState.value as FilePreviewUiState.Editing
        assertEquals("new text", editing.draft)

        viewModel.savePreviewEdit()
        advanceUntilIdle()

        assertEquals("new text", savedText)
        assertEquals(FilePreviewUiState.Idle, viewModel.previewState.value)
    }

    @Test
    fun editConflictKeepsDraftAndShowsError() = runTest(dispatcher) {
        val file = FileNode("notes.txt", "/notes.txt", isDirectory = false, mimeType = "text/plain")
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent =
                FilePreviewContent.Text("old text", entityTag = "\"v1\"")

            override suspend fun updateText(
                file: FileNode,
                original: FilePreviewContent.Text,
                text: String,
            ) {
                throw WebDavException.PreconditionFailed()
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()
        viewModel.preview(file)
        advanceUntilIdle()
        viewModel.startPreviewEdit()
        viewModel.updatePreviewDraft("my draft")

        viewModel.savePreviewEdit()
        advanceUntilIdle()

        val editing = viewModel.previewState.value as FilePreviewUiState.Editing
        assertEquals("my draft", editing.draft)
        assertTrue(editing.error.orEmpty().contains("已被修改"))
    }

    @Test
    fun truncatedTextCannotEnterEditMode() = runTest(dispatcher) {
        val file = FileNode("large.txt", "/large.txt", isDirectory = false, mimeType = "text/plain")
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent =
                FilePreviewContent.Text("prefix", truncated = true, entityTag = "\"v1\"")
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()
        viewModel.preview(file)
        advanceUntilIdle()

        viewModel.startPreviewEdit()

        assertTrue(viewModel.previewState.value is FilePreviewUiState.Content)
    }

    @Test
    fun textWithoutStrongPreviewEtagCannotEnterEditMode() = runTest(dispatcher) {
        val file = FileNode("notes.txt", "/notes.txt", isDirectory = false, mimeType = "text/plain")
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun preview(file: FileNode): FilePreviewContent =
                FilePreviewContent.Text("content", entityTag = "W/\"weak\"")
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()
        viewModel.preview(file)
        advanceUntilIdle()

        viewModel.startPreviewEdit()

        assertTrue(viewModel.previewState.value is FilePreviewUiState.Content)
    }

    private class FakeFileRepository(
        private val loader: suspend (WebDavPath) -> List<FileNode>,
    ) : FileRepository {
        override suspend fun list(path: WebDavPath): List<FileNode> = loader(path)
    }
}
