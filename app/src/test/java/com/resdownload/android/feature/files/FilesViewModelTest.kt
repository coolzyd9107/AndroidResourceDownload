package com.resdownload.android.feature.files

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import com.resdownload.android.data.file.FileRepository
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.model.FilePreviewContent
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun toggleSelectAllSelectsEveryItemThenClearsSelection() = runTest(dispatcher) {
        val files = listOf(
            FileNode("folder", "/folder", isDirectory = true),
            FileNode("notes.txt", "/notes.txt", isDirectory = false),
            FileNode("image.png", "/image.png", isDirectory = false),
        )
        val viewModel = FilesViewModel(FakeFileRepository { files })
        advanceUntilIdle()
        viewModel.enterMultiSelect()
        viewModel.toggleSelection(files.first().path)

        viewModel.toggleSelectAll()

        assertEquals(files.map(FileNode::path).toSet(), viewModel.selectedPaths.value)

        viewModel.toggleSelectAll()

        assertTrue(viewModel.selectedPaths.value.isEmpty())
        assertTrue(viewModel.multiSelectMode.value)
    }

    @Test
    fun invertSelectionSelectsOnlyCurrentComplement() = runTest(dispatcher) {
        val files = listOf(
            FileNode("folder", "/folder", isDirectory = true),
            FileNode("notes.txt", "/notes.txt", isDirectory = false),
            FileNode("image.png", "/image.png", isDirectory = false),
        )
        val viewModel = FilesViewModel(FakeFileRepository { files })
        advanceUntilIdle()
        viewModel.enterMultiSelect()
        viewModel.toggleSelection("/folder")
        viewModel.toggleSelection("/image.png")

        viewModel.invertSelection()

        assertEquals(setOf("/notes.txt"), viewModel.selectedPaths.value)
    }

    @Test
    fun selectAllCanUseCrossDirectorySearchResults() = runTest(dispatcher) {
        val viewModel = FilesViewModel(FakeFileRepository { emptyList() })
        advanceUntilIdle()
        val searchResults = listOf(
            FileNode("one.txt", "/first/one.txt", isDirectory = false),
            FileNode("two.txt", "/second/nested/two.txt", isDirectory = false),
        )

        viewModel.enterMultiSelect()
        viewModel.toggleSelectAll(searchResults)

        assertEquals(searchResults.mapTo(mutableSetOf(), FileNode::path), viewModel.selectedPaths.value)
    }

    @Test
    fun batchDestinationMustBeValidForEverySelectedSource() {
        val sources = listOf(
            WebDavPath.parseDecoded("/safe.txt"),
            WebDavPath.parseDecoded("/folder"),
        )

        assertFalse(
            isValidTransferDestination(
                sources,
                WebDavPath.parseDecoded("/folder/nested"),
            ),
        )
        assertTrue(
            isValidTransferDestination(
                sources,
                WebDavPath.parseDecoded("/archive"),
            ),
        )
        assertFalse(
            isValidTransferDestination(
                listOf(
                    WebDavPath.parseDecoded("/first/report.pdf"),
                    WebDavPath.parseDecoded("/second/report.pdf"),
                ),
                WebDavPath.parseDecoded("/archive"),
            ),
        )
        assertFalse(
            isValidTransferDestination(
                listOf(
                    WebDavPath.parseDecoded("/folder"),
                    WebDavPath.parseDecoded("/folder/nested/file.txt"),
                ),
                WebDavPath.parseDecoded("/archive"),
            ),
        )
    }

    @Test
    fun rootSearchFindsMatchingDirectoriesAndFilesAtEveryDepth() = runTest(dispatcher) {
        val tree = mapOf(
            "/" to listOf(
                FileNode("Reports", "/Reports", isDirectory = true),
                FileNode("Archive", "/Archive", isDirectory = true),
            ),
            "/Reports" to listOf(
                FileNode("2026", "/Reports/2026", isDirectory = true),
                FileNode("summary.txt", "/Reports/summary.txt", isDirectory = false),
            ),
            "/Reports/2026" to listOf(
                FileNode("FINAL-REPORT.pdf", "/Reports/2026/FINAL-REPORT.pdf", isDirectory = false),
            ),
            "/Archive" to emptyList(),
        )
        val viewModel = FilesViewModel(FakeFileRepository { path -> tree[path.toString()].orEmpty() })
        advanceUntilIdle()

        viewModel.search("report", FileSearchScope.ROOT)
        advanceUntilIdle()

        val state = viewModel.searchState.value as FileSearchUiState.Success
        assertEquals(
            setOf("/Reports", "/Reports/2026/FINAL-REPORT.pdf"),
            state.files.map(FileNode::path).toSet(),
        )
        assertFalse(state.incomplete)
        assertEquals("/", state.request.basePath.toString())
    }

    @Test
    fun currentDirectorySearchExcludesSiblingSubtrees() = runTest(dispatcher) {
        val tree = mapOf(
            "/" to listOf(
                FileNode("Current", "/Current", isDirectory = true),
                FileNode("Sibling", "/Sibling", isDirectory = true),
            ),
            "/Current" to listOf(
                FileNode("nested", "/Current/nested", isDirectory = true),
            ),
            "/Current/nested" to listOf(
                FileNode("target.txt", "/Current/nested/target.txt", isDirectory = false),
            ),
            "/Sibling" to listOf(
                FileNode("target.txt", "/Sibling/target.txt", isDirectory = false),
            ),
        )
        val viewModel = FilesViewModel(FakeFileRepository { path -> tree[path.toString()].orEmpty() })
        advanceUntilIdle()
        viewModel.openDirectory(WebDavPath.parseDecoded("/Current"))
        advanceUntilIdle()

        viewModel.search("TARGET", FileSearchScope.CURRENT_DIRECTORY)
        advanceUntilIdle()

        val state = viewModel.searchState.value as FileSearchUiState.Success
        assertEquals(listOf("/Current/nested/target.txt"), state.files.map(FileNode::path))
        assertEquals("/Current", state.request.basePath.toString())
    }

    @Test
    fun inaccessibleDescendantProducesExplicitPartialResults() = runTest(dispatcher) {
        val viewModel = FilesViewModel(
            FakeFileRepository { path ->
                when (path.toString()) {
                    "/" -> listOf(
                        FileNode("blocked", "/blocked", isDirectory = true),
                        FileNode("match.txt", "/match.txt", isDirectory = false),
                    )
                    "/blocked" -> throw WebDavException.PermissionDenied()
                    else -> emptyList()
                }
            },
        )
        advanceUntilIdle()

        viewModel.search("match", FileSearchScope.ROOT)
        advanceUntilIdle()

        val state = viewModel.searchState.value as FileSearchUiState.Success
        assertEquals(listOf("/match.txt"), state.files.map(FileNode::path))
        assertTrue(state.incomplete)
    }

    @Test
    fun cancellingSearchPreventsLateResultPublication() = runTest(dispatcher) {
        val searchResult = CompletableDeferred<List<FileNode>>()
        var rootCalls = 0
        val viewModel = FilesViewModel(
            FakeFileRepository { path ->
                if (path.isRoot) {
                    rootCalls++
                    if (rootCalls == 1) emptyList() else searchResult.await()
                } else {
                    emptyList()
                }
            },
        )
        runCurrent()
        viewModel.search("late", FileSearchScope.ROOT)
        runCurrent()

        viewModel.cancelSearch()
        searchResult.complete(listOf(FileNode("late.txt", "/late.txt", isDirectory = false)))
        advanceUntilIdle()

        assertEquals(FileSearchUiState.Idle, viewModel.searchState.value)
    }

    @Test
    fun cancellationIgnoringOldSearchCannotReplaceNewerResults() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<List<FileNode>>()
        var rootCalls = 0
        val viewModel = FilesViewModel(
            FakeFileRepository { path ->
                if (!path.isRoot) return@FakeFileRepository emptyList()
                rootCalls++
                when (rootCalls) {
                    1 -> emptyList()
                    2 -> try {
                        oldResult.await()
                    } catch (_: CancellationException) {
                        withContext(NonCancellable) { oldResult.await() }
                    }
                    else -> listOf(FileNode("new.txt", "/new.txt", isDirectory = false))
                }
            },
        )
        runCurrent()
        viewModel.search("old", FileSearchScope.ROOT)
        runCurrent()

        viewModel.search("new", FileSearchScope.ROOT)
        runCurrent()
        val newState = viewModel.searchState.value as FileSearchUiState.Success
        assertEquals(listOf("/new.txt"), newState.files.map(FileNode::path))

        oldResult.complete(listOf(FileNode("old.txt", "/old.txt", isDirectory = false)))
        advanceUntilIdle()

        val finalState = viewModel.searchState.value as FileSearchUiState.Success
        assertEquals(listOf("/new.txt"), finalState.files.map(FileNode::path))
    }

    @Test
    fun recursiveSearchRejectsResourcesOutsideListedDirectory() = runTest(dispatcher) {
        val request = FileSearchRequest(
            query = "secret",
            scope = FileSearchScope.CURRENT_DIRECTORY,
            basePath = WebDavPath.parseDecoded("/allowed"),
        )

        val result = searchFilesRecursively(
            request = request,
            listDirectory = {
                listOf(
                    FileNode("secret.txt", "/outside/secret.txt", isDirectory = false),
                    FileNode("secret.txt", "/allowed/nested/secret.txt", isDirectory = false),
                )
            },
        )

        assertTrue(result.files.isEmpty())
        assertTrue(result.incomplete)
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
    fun renameFileMovesWithinParentWithoutOverwriteAndRefreshes() = runTest(dispatcher) {
        var actualSource: WebDavPath? = null
        var actualDestination: WebDavPath? = null
        var actualOverwrite: Boolean? = null
        var actualCollection: Boolean? = null
        var actualEtag: String? = null
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
                actualSource = source
                actualDestination = destination
                actualOverwrite = overwrite
                actualCollection = sourceIsCollection
                actualEtag = sourceEtag
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.rename(
            source = WebDavPath.parseDecoded("/资料/old.txt"),
            sourceIsDirectory = false,
            newName = "new.txt",
            sourceEtag = "\"v1\"",
        )
        advanceUntilIdle()

        assertEquals("/资料/old.txt", actualSource.toString())
        assertEquals("/资料/new.txt", actualDestination.toString())
        assertFalse(requireNotNull(actualOverwrite))
        assertFalse(requireNotNull(actualCollection))
        assertEquals("\"v1\"", actualEtag)
        assertEquals(2, listCalls)
        assertEquals(FileMutationState.Idle, viewModel.mutationState.value)
    }

    @Test
    fun renameDirectoryUsesCollectionMove() = runTest(dispatcher) {
        var actualDestination: WebDavPath? = null
        var actualCollection = false
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun move(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                actualDestination = destination
                actualCollection = sourceIsCollection
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.rename(
            source = WebDavPath.parseDecoded("/旧目录"),
            sourceIsDirectory = true,
            newName = "新目录",
        )
        advanceUntilIdle()

        assertEquals("/新目录", actualDestination.toString())
        assertTrue(actualCollection)
    }

    @Test
    fun renameRejectsUnchangedAndUnsafeNamesBeforeRepositoryCall() = runTest(dispatcher) {
        var moveCalls = 0
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun move(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                moveCalls++
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()
        val source = WebDavPath.parseDecoded("/notes.txt")

        viewModel.rename(source, sourceIsDirectory = false, newName = "notes.txt")
        assertTrue((viewModel.mutationState.value as FileMutationState.Failed).message.contains("相同"))
        viewModel.dismissMutation()

        viewModel.rename(source, sourceIsDirectory = false, newName = "../notes.txt")

        assertTrue((viewModel.mutationState.value as FileMutationState.Failed).message.contains("无效字符"))
        assertEquals(0, moveCalls)
    }

    @Test
    fun renamePreservesBoundarySpacesInNewName() = runTest(dispatcher) {
        var actualDestination: WebDavPath? = null
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun move(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                actualDestination = destination
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.rename(
            source = WebDavPath.parseDecoded("/notes.txt"),
            sourceIsDirectory = false,
            newName = " notes.txt ",
        )
        advanceUntilIdle()

        assertEquals("/ notes.txt ", actualDestination.toString())
    }

    @Test
    fun renameConflictNeverOffersOverwrite() = runTest(dispatcher) {
        val repository = object : FileRepository {
            override suspend fun list(path: WebDavPath): List<FileNode> = emptyList()

            override suspend fun move(
                source: WebDavPath,
                destination: WebDavPath,
                overwrite: Boolean,
                sourceIsCollection: Boolean,
                sourceEtag: String?,
            ) {
                throw WebDavException.PreconditionFailed()
            }
        }
        val viewModel = FilesViewModel(repository)
        runCurrent()

        viewModel.rename(
            source = WebDavPath.parseDecoded("/notes.txt"),
            sourceIsDirectory = false,
            newName = "existing.txt",
        )
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
