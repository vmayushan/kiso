package kiso.core.di

import kiso.core.persistence.mongo.DefaultMongoConfig
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.mongo.MongoConfig
import org.koin.dsl.module
import org.litote.kmongo.KMongo



val mongoModule = module {
    single { DefaultMongoConfig }
    single { ExecutionJobDao(client = KMongo.createClient(get<MongoConfig>().uri)) }
}