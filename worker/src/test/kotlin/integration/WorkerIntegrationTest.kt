package integration

import kiso.core.persistence.serialization.deserializeEvent
import kiso.core.persistence.serialization.serializeJob
import kiso.core.models.*
import kiso.core.models.progress.*
import kiso.worker.Worker
import kiso.worker.config.WorkerConfig
import kiso.worker.di.dockerModule
import kiso.worker.di.mainModule
import kiso.core.di.redisModule
import kiso.core.persistence.redis.RedisConfig
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.*
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkerIntegrationTest {
    companion object {
        private lateinit var redisStreamClient: RedisStreamClient
        private lateinit var worker: Worker

        @BeforeAll
        @JvmStatic
        fun startWorker(): Unit = runBlocking {
            val configModule = module {
                single { RedisConfig(uri = "redis://localhost:6379") }
                single { WorkerConfig(creationParallelism = 1, executionParallelism = 1) }
            }
            val app = startKoin { modules(configModule, dockerModule, redisModule, mainModule) }
            redisStreamClient = app.koin.get()
            redisStreamClient.deleteStream(StreamName.jobsQueue)

            worker = app.koin.get()
            worker.init()
            worker.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWorker(): Unit = runBlocking {
            worker.stop()
        }
    }

    @Test
    fun `csharp hello world job`(): Unit = runBlocking {
        // Arrange
        val csharpJob = ExecutionJob(
            UUID.randomUUID().toString(),
            Language.CSharp,
            """Console.WriteLine("Hello from .NET!");""",
            packages = emptyList()
        )

        // Act
        redisStreamClient.add(StreamName.jobsQueue, serializeJob(csharpJob))

        // Assert
        val result = waitForJobCompletion(csharpJob, 1.minutes)

        assertTrue { result.isCompleted }
        assertTrue {
            result.progress
                .filterIsInstance<ExecutionLogEvent>()
                .any { it.log.contains("Hello from .NET!") }
        }
    }

    @Test
    fun `python hello world job`(): Unit = runBlocking {
        // Arrange
        val pythonJob = ExecutionJob(
            UUID.randomUUID().toString(),
            Language.Python,
            """print("Hello from Python!")""",
            packages = emptyList()
        )

        // Act
        redisStreamClient.add(StreamName.jobsQueue, serializeJob(pythonJob))

        // Assert
        val result = waitForJobCompletion(pythonJob, 1.minutes)
        assertTrue { result.isCompleted }
        assertTrue {
            result.progress
                .filterIsInstance<ExecutionLogEvent>()
                .any { it.log.contains("Hello from Python!") }
        }
    }

    @Test
    fun `csharp install package NewtonsoftJson`(): Unit = runBlocking {
        // Arrange
        val csharpJob = ExecutionJob(
            UUID.randomUUID().toString(),
            Language.CSharp,
            """
                using Newtonsoft.Json;
                var list = new List<string> { "c#", "python", "kotlin"};
                var serialized = JsonConvert.SerializeObject(list);
                Console.WriteLine(serialized);
            """.trimIndent(),
            packages = listOf(Package("Newtonsoft.Json"))
        )

        // Act
        redisStreamClient.add(StreamName.jobsQueue, serializeJob(csharpJob))

        // Assert
        val result = waitForJobCompletion(csharpJob, 1.minutes)
        assertTrue { result.isCompleted }
        assertTrue {
            result.progress
                .filterIsInstance<ExecutionLogEvent>()
                .any { it.log.contains("""["c#","python","kotlin"]""") }
        }
    }

    private suspend fun waitForJobCompletion(job: ExecutionJob, timeout: Duration): ExecutionJobResult {
        var completed = false
        var offset = "0"
        val progressEvents = ArrayList<ExecutionProgressEvent>()
        withTimeout(timeout) {
            while (!completed) {
                val messages = redisStreamClient.readMessages(StreamName.jobProgress(job.id), offset).toList()
                if (messages.isNotEmpty()) {
                    offset = messages.last().id
                    messages.map { deserializeEvent(it.body) }.forEach {
                        progressEvents.add(it)

                        if (it is ExecutionCompletedEvent) {
                            completed = true
                        }
                    }
                } else {
                    delay(1.seconds)
                }
            }
        }

        return ExecutionJobResult(completed, progressEvents)
    }

    data class ExecutionJobResult(val isCompleted: Boolean, val progress: List<ExecutionProgressEvent>)
}