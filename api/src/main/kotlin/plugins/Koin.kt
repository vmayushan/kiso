package kiso.api.plugins

import kiso.api.di.mainModule
import kiso.core.di.mongoModule
import kiso.core.di.redisModule
import org.koin.core.context.startKoin


fun configureKoin() {
    startKoin {
        modules(redisModule, mongoModule, mainModule)
    }
}