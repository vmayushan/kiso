package integration

import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.models.ExecutionStep
import kiso.core.models.progress.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.litote.kmongo.KMongo
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionJobDaoIntegrationTest {
    @Test
    fun `insert execution job progress to db and read it`(): Unit = runBlocking {
        // Arrange
        val jobId = UUID.randomUUID().toString()
        val events = listOf(
            ExecutionStartedEvent,
            ExecutionStepStartedEvent(ExecutionStep.Setup),
            ExecutionStepCompletedEvent(ExecutionStep.Setup, success = true, duration = 42L, errorMessage = null),
            ExecutionCompletedEvent
        )

        // Act, Assert
        val dao = ExecutionJobDao(KMongo.createClient())
        assertTrue { dao.insertJob(jobId) }
        assertTrue { dao.updateJobEvents(jobId, events) }

        val eventsFromDb = dao.getJobById(jobId)

        assertNotNull(eventsFromDb)
        assertEquals(events.count(), eventsFromDb.count())
    }
}