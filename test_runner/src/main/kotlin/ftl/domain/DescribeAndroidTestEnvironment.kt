package ftl.domain

import flank.common.logLn
import ftl.api.Platform
import ftl.api.fetchDeviceModelAndroid
import ftl.api.fetchIpBlocks
import ftl.api.fetchNetworkProfiles
import ftl.api.fetchOrientation
import ftl.api.fetchSoftwareCatalog
import ftl.args.AndroidArgs
import ftl.client.google.AndroidCatalog
import ftl.environment.android.toCliTable
import ftl.environment.common.toCliTable
import ftl.environment.toCliTable
import java.nio.file.Paths

interface DescribeAndroidTestEnvironment {
    val configPath: String
}

fun DescribeAndroidTestEnvironment.invoke() {
    val projectId = AndroidArgs.loadOrDefault(Paths.get(configPath)).project
    logLn(fetchDeviceModelAndroid(projectId).toCliTable()) // TODO move toCliTable() to presentation layer during refactor of presentation after #1728
    logLn(AndroidCatalog.supportedVersionsAsTable(projectId))
    logLn(AndroidCatalog.localesAsTable(projectId))
    logLn(fetchSoftwareCatalog().toCliTable())
    logLn(fetchNetworkProfiles().toCliTable())
    logLn(fetchOrientation(projectId, Platform.ANDROID).toCliTable()) // TODO move toCliTable() to presentation layer during refactor of presentation after #1728
    logLn(fetchIpBlocks().toCliTable()) // TODO move toCliTable() to presentation layer during refactor of presentation after #1728
}
