package kiso.reducer.config

import java.net.InetAddress

object ReducerConfig {
    val instanceName: String = InetAddress.getLocalHost().hostName
}