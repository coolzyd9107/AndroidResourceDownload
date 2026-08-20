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

    private class FakeFileRepository(
        private val loader: suspend (WebDavPath) -> List<FileNode>,
    ) : FileRepository {
        override suspend fun list(path: WebDavPath): List<FileNode> = loader(path)
    }
}
