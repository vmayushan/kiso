package kiso.core.persistence.redis

data class RedisConfig(
    val uri: String
)

val DefaultRedisConfig = RedisConfig(
    uri = System.getenv("REDIS_CONNECTION_STRING") ?: "redis://localhost:6379"
)