package kiso.worker.docker.container

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val config = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .build()

private val dockerHttpClient: ApacheDockerHttpClient = ApacheDockerHttpClient.Builder()
    .dockerHost(config.dockerHost)
    .sslConfig(config.sslConfig)
    .connectionTimeout(10.seconds.toJavaDuration())
    .responseTimeout(30.seconds.toJavaDuration())
    .build()

var DockerClient: DockerClient = DockerClientImpl
    .getInstance(config, dockerHttpClient)