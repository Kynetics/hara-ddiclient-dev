/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.integrationtest.utils

import org.eclipse.hara.ddiclient.api.PathResolver
import org.eclipse.hara.ddiclient.api.Updater
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.integrationtest.api.management.ActionStatus
import java.io.File
import java.util.*

val LOG_HTTP: Boolean = System.getProperty("LOG_HTTP", "false").toBoolean()
val LOG_INTERNAL: Boolean = System.getProperty("LOG_INTERNAL", "false").toBoolean()

/**
 * @author Daniele Sergio
 */
object TestUtils {

    const val APP_DISTRIBUTION_ID = 3
    const val OS_DISTRIBUTION_ID = 2
    const val OS_WITH_APPS_DISTRIBUTION_ID = 1

    data class TargetDeployments(
        val targetId: String,
        val targetToken: String,
        val deploymentInfo: List<DeploymentInfo>
    ) {
        data class DeploymentInfo(
            val actionId: Int,
            val actionStatusOnFinish: ActionStatus,
            val filesDownloadedPairedWithServerFile: Set<Pair<String, String>>
        )
    }

    const val tenantName = "DEFAULT"
    val tenantNameToLower = tenantName.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val basic = Credentials.basic("test", "test")
    const val hawkbitUrl = "http://localhost:8080"
    const val downloadRootDirPath = "./build/test/download/"
    const val gatewayToken = "66076ab945a127dd80b15e9011995109"
    val directoryDataProvider = object : DirectoryForArtifactsProvider { override fun directoryForArtifacts(): File = File(
        downloadRootDirPath
    ) }
    val pathResolver = PathResolver(directoryDataProvider)
    val configDataProvider = object : ConfigDataProvider {}
    val downloadBehavior = object : DownloadBehavior {
        override fun onAttempt(
            attempt: Int,
            artifactId: String,
            previousError: Throwable?
        ): DownloadBehavior.Try {
            return if (attempt == 1){
                DownloadBehavior.Try.After(0)
            } else {
                DownloadBehavior.Try.Stop
            }
        }
    }
    val updater = object : Updater {
        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult {
            println("APPLY UPDATE $modules")
            messenger.sendMessageToServer("Applying the update...")
            messenger.sendMessageToServer("Update applied")
            return Updater.UpdateResult(true)
        }
    }

    private val serverFilesMappedToLocantionAndMd5 = mapOf(
        "test1" to Pair(
            "docker/test/artifactrepo/$tenantName/4b/5a/b54e43082887d1e7cdb10b7a21fe4a1e56b44b5a",
            "2490a3d39b0004e4afeb517ef0ddbe2d"),
        "test2" to Pair(
            "docker/test/artifactrepo/$tenantName/b6/1e/a096a9d3cb96fa4cf6c63bd736a84cb7a7e4b61e",
            "b0b3b0dbf5330e3179c6ae3e0ac524c9"),
        "test3" to Pair(
            "docker/test/artifactrepo/$tenantName/bf/94/cde0c01b26634f869bb876326e4fbe969792bf94",
            "2244fbd6bee5dcbe312e387c062ce6e6"),
        "test4" to Pair(
            "docker/test/artifactrepo/$tenantName/dd/0a/07fa4d03ac54d0b2a52f23d8e878c96db7aadd0a",
            "94424c5ce3f8c57a5b26d02f37dc06fc"),
    )

    val md5OfFileNamed: (String) -> String = { key -> serverFilesMappedToLocantionAndMd5.getValue(key).second }
    val locationOfFileNamed: (String) -> String = { key -> serverFilesMappedToLocantionAndMd5.getValue(key).first }

    val test1Artifact = Updater.SwModule.Artifact("test1", Updater.Hashes("",
        md5OfFileNamed("test1")
    ), 0)
    val test2Artifact = Updater.SwModule.Artifact("test2", Updater.Hashes("",
        md5OfFileNamed("test2")
    ), 0)
    val test3Artifact = Updater.SwModule.Artifact("test3", Updater.Hashes("",
        md5OfFileNamed("test3")
    ), 0)
    val test4Artifact = Updater.SwModule.Artifact("test4", Updater.Hashes("",
        md5OfFileNamed("test4")
    ), 0)

    val endMessagesOnSuccessUpdate = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.finished,
                    listOf("Details:")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Update applied")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Applying the update...")
            )
    )

    val waitingForDownloadAuthorizationMessage = ActionStatus.ContentEntry(
        ActionStatus.ContentEntry.Type.running,
        listOf("Waiting authorization to download")
    )

    val waitingForUpdateAuthorizationMessage = ActionStatus.ContentEntry(
        ActionStatus.ContentEntry.Type.running,
        listOf("Waiting authorization to update")
    )

    val messagesOnSoftDownloadAuthorization = arrayOf(
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Authorization granted for downloading files")
        ),
        waitingForDownloadAuthorizationMessage
    )

    val messagesOnSoftUpdateAuthorization = arrayOf(
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Authorization granted for update")
        ),
        waitingForUpdateAuthorizationMessage
    )

    val firstActionWithAssignmentEntry = ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Assignment initiated by user 'test'")
    )

    val targetRetrievedUpdateAction = ActionStatus.ContentEntry(
        ActionStatus.ContentEntry.Type.retrieved,
        listOf("Update Server: Target retrieved update action and should start now the download.")
    )

    val firstActionsOnTargetDeployment = arrayOf(
        targetRetrievedUpdateAction,
        firstActionWithAssignmentEntry
    )

    fun messagesOnSuccessfullyDownloadDistribution(
        md5: String, targetId: String, softwareModuleId: String,
        fileName: String) = arrayOf(
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Successfully downloaded all files")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 $md5"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf(
                "Update Server: Target downloads /$tenantNameToLower/controller/v1/$targetId/softwaremodules/$softwareModuleId/artifacts/$fileName")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Start downloading 1 files")
        )
    )
}


fun OkHttpClient.Builder.addOkhttpLogger(): OkHttpClient.Builder = apply {
    val logger = HttpLoggingInterceptor.Logger { message ->
        if (LOG_HTTP) {
            "OkHttp: $message".log()
        }
    }
    addInterceptor(HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
}
