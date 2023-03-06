package kiso.core.persistence.mongo

data class MongoConfig(
    val uri: String
)

val DefaultMongoConfig = MongoConfig(
    uri = System.getenv("MONGO_CONNECTION_STRING") ?: "mongodb://localhost"
)