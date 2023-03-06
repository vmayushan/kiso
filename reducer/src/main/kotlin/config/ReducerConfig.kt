package kiso.reducer.config

import java.net.InetAddress

object ReducerConfig {
    val instanceName = InetAddress.getLocalHost().hostName
}