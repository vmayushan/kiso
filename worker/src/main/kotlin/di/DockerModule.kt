package kiso.worker.di

import kiso.core.models.Language
import kiso.worker.docker.pool.DockerContainerPool
import kiso.worker.docker.pool.DockerContainerPoolConfig
import kiso.worker.imageConfig

import org.koin.dsl.module
import java.net.InetAddress


val dockerModule = module {
    single {
        DockerContainerPoolConfig(
            nodeName = InetAddress.getLocalHost().hostName,
            images = listOf(Language.CSharp.imageConfig, Language.Python.imageConfig)
        )
    }
    single { DockerContainerPool(get()) }
}