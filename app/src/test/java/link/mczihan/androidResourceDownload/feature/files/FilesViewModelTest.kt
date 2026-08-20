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

    private class FakeFileRepository(
        private val loader: suspend (WebDavPath) -> List<FileNode>,
    ) : FileRepository {
        override suspend fun list(path: WebDavPath): List<FileNode> = loader(path)
    }
}
