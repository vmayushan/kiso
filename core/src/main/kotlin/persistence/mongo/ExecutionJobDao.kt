package kiso.core.persistence.mongo

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import kiso.core.models.progress.ExecutionProgressEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.*

@Serializable
data class JobDocument(@SerialName("_id") val jobId: String, val events: List<ExecutionProgressEvent>)

class ExecutionJobDao(client: MongoClient) {
    private val collection: MongoCollection<JobDocument>

    init {
        collection = client
            .getDatabase("kiso")
            .getCollection<JobDocument>("jobs")
    }

    fun insertJob(jobId: String): Boolean = collection
        .insertOne(JobDocument(jobId, emptyList()))
        .insertedId != null

    fun getJobById(jobId: String): List<ExecutionProgressEvent>? = collection
        .findOne { JobDocument::jobId eq jobId }?.events

    fun updateJobEvents(jobId: String, events: List<ExecutionProgressEvent>): Boolean = collection
        .updateOne(JobDocument::jobId eq jobId, setValue(JobDocument::events, events))
        .modifiedCount > 0

    fun deleteJob(jobId: String): Boolean = collection
        .deleteOne(JobDocument::jobId eq jobId)
        .deletedCount > 0
}