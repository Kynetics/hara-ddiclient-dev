/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.eclipse.hara.ddi.api.DdiClientDefaultImpl
import org.eclipse.hara.ddiclient.api.actors.AbstractActor
import org.eclipse.hara.ddiclient.api.actors.ActorRef
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager
import org.eclipse.hara.ddiclient.api.actors.RootActor
import org.eclipse.hara.ddiclient.api.actors.HaraClientContext
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.api.actors.sendMessageToChannelIfOpen
import org.slf4j.LoggerFactory

class HaraClientDefaultImpl : HaraClient {

    private val LOG = LoggerFactory.getLogger(this::class.java)
    private var rootActor: ActorRef? = null

    private val debouncingForcePingChannel: Channel<ConnectionManager.Companion.Message.In.ForcePing> =
        Channel(1, BufferOverflow.DROP_LATEST)

    @OptIn(ObsoleteCoroutinesApi::class)
    override fun init(
        haraClientData: HaraClientData,
        directoryForArtifactsProvider: DirectoryForArtifactsProvider,
        configDataProvider: ConfigDataProvider,
        softDeploymentPermitProvider: DeploymentPermitProvider,
        messageListeners: List<MessageListener>,
        updaters: List<Updater>,
        downloadBehavior: DownloadBehavior,
        forceDeploymentPermitProvider: DeploymentPermitProvider,
        httpBuilder: OkHttpClient.Builder,
        scope: CoroutineScope) {
        runBlocking {
            scope.launch {
                for(msg in debouncingForcePingChannel){
                    rootActor?.send(msg)
                    delay(FORCE_PING_DEBOUNCING_TIME)
                }
            }
        }
        rootActor = AbstractActor.actorOf("rootActor", HaraClientContext(
                DdiClientDefaultImpl.of(haraClientData, httpBuilder),
                UpdaterRegistry(*updaters.toTypedArray()),
                configDataProvider,
                PathResolver(directoryForArtifactsProvider),
                softDeploymentPermitProvider,
                messageListeners,
                downloadBehavior,
                forceDeploymentPermitProvider,
            ),
            scope = scope
        ) { RootActor.of(it) }
    }

    override fun startAsync() = runBlocking { rootActor!!.send(ConnectionManager.Companion.Message.In.Start) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun stop() = runBlocking {
        rootActor!!.sendMessageToChannelIfOpen(ConnectionManager.Companion.Message.In.Stop)
        if(!debouncingForcePingChannel.isClosedForSend){
            debouncingForcePingChannel.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun forcePing() = runBlocking {
        if (!debouncingForcePingChannel.isClosedForSend) {
            debouncingForcePingChannel.send(ConnectionManager.Companion.Message.In.ForcePing)
        } else {
            LOG.warn("HaraClient is stopped. Cannot force ping.")
        }
    }

    companion object{
        const val FORCE_PING_DEBOUNCING_TIME = 30_000L
    }
}
