package flank.corellium.domain.run.test.android.step

import flank.corellium.api.CorelliumApi
import flank.corellium.domain.RunTestCorelliumAndroid
import flank.corellium.domain.RunTestCorelliumAndroid.Args
import flank.corellium.domain.RunTestCorelliumAndroid.Authorize
import flank.corellium.domain.RunTestCorelliumAndroid.ExecuteTests
import flank.corellium.domain.RunTestCorelliumAndroid.ExecuteTests.ADB_LOG
import flank.corellium.domain.RunTestCorelliumAndroid.InstallApks
import flank.corellium.domain.RunTestCorelliumAndroid.InvokeDevices
import flank.corellium.domain.RunTestCorelliumAndroid.ParseApkInfo
import flank.corellium.domain.RunTestCorelliumAndroid.PrepareShards
import flank.corellium.domain.invalidLog
import flank.corellium.domain.stubCredentials
import flank.corellium.domain.validLog
import flank.exection.parallel.Parallel
import flank.exection.parallel.ParallelState
import flank.exection.parallel.invoke
import flank.exection.parallel.select
import flank.exection.parallel.type
import flank.log.Event
import flank.log.Output
import flank.shard.Shard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ExecuteTestsKtTest {

    private val dir = File(Args.DefaultOutputDir.new)
    private val instanceId = "1"

    private val initial: ParallelState = mapOf(
        Args to Args(
            credentials = stubCredentials,
            apks = emptyList(),
            maxShardsCount = 1,
            outputDir = dir.path
        ),
        PrepareShards to listOf((0..2).map { Shard.App("$it", emptyList()) }),
        ParseApkInfo to RunTestCorelliumAndroid.Info(),
        Authorize to Unit,
        InvokeDevices to listOf(instanceId),
        InstallApks to Unit,
    )

    private val execute = setOf(executeTests)

    // simulate additional unneeded input that will be omitted.
    private val additionalInput = (0..1000).map(Int::toString).asFlow().onStart { delay(500) }
    private fun corelliumApi(log: String) = CorelliumApi(
        executeTest = { listOf(instanceId to flowOf(log.lines().asFlow(), additionalInput).flattenConcat()) }
    )

    @Before
    fun setUp() {
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * Valid console output should be completely saved in file, parsed and returned as testResult.
     */
    @Test
    fun happyPath() {
        // given
        val args = initial + mapOf(
            type<CorelliumApi>() to corelliumApi(validLog)
        )

        // when
        val testResult = runBlocking { execute(args).last() }

        // then
        assertEquals(9, testResult.select(ExecuteTests).first().size)

        // Right after reading the required results count from validLog the stream is closing.
        // Saved log is same as validLog without unneeded additionalInput.
        assertEquals(
            validLog,
            dir.resolve(ADB_LOG)
                .resolve(instanceId)
                .readText()
                .trimEnd()
        )
    }

    /**
     * On parsing error, the task will send the [RunTestCorelliumAndroid.ExecuteTests.Error] through [Output].
     */
    @Test
    fun error() {
        // given
        val events = mutableListOf<Event<*>>()
        val out: Output = { events += this as Event<*> }
        val args = initial + mapOf(
            type<CorelliumApi>() to corelliumApi(invalidLog),
            Parallel.Logger to out
        )
        // when
        val testResult = runBlocking { execute(args).last().select(ExecuteTests) }

        // then
        assertTrue(testResult.first().isNotEmpty()) // Valid lines parsed before error will be returned

        val error = events.mapNotNull { it.value as? ExecuteTests.Error }.first() // Obtain error
        assertEquals(instanceId, error.id) // Error should contain correct instanceId

        val lines = dir.resolve(ADB_LOG).resolve(instanceId).readLines() // Read log saved in file
        assertTrue(lines.size > error.lines.last) // Task can save more output lines than was marked in error which is expected behaviour

        val invalid = lines.indexOfFirst { it.endsWith("INVALID LINE") } + 1 // Obtain invalid line number
        assertTrue(invalid in error.lines) // Error should reference affected lines
    }
}
