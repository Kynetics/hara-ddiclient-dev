/*
 *
 *  * Copyright © 2017-2024  Kynetics, Inc.
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.hara.ddiclient.virtualdevice

import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class VirtualDeviceTest {

    companion object {
        const val HAWKBIT_URL = "http://localhost:8080"
        val BASIC_AUTH = Credentials.basic("test", "test")
        const val GATEWAY_TOKEN = "66076ab945a127dd80b15e9011995109"

    }

    private val devicesControllerId = mutableListOf<String>()
    private lateinit var managementApi: ManagementApi

    @BeforeClass
    fun setUp() {
        managementApi = ManagementClient.createManagementApi(HAWKBIT_URL)
        runBlocking {
            managementApi.setPollingTime(BASIC_AUTH, ServerSystemConfig("00:00:10"))
        }
    }

    @Test(enabled = true, timeOut = 60_000)
    fun testVirtualDeviceCreationAndPolling() {
        runBlocking {
            val cfg = Configuration.default().copy(url = HAWKBIT_URL)
                .copy(poolSize = 2)
                .copy(gatewayToken = GATEWAY_TOKEN)
                .copy(controllerIdGenerator = {
                    val id = "VirtualDevice-number-$it"
                    devicesControllerId.add(id)
                    id
                })
            val device = VirtualDevice(cfg)
            launch {
                device.start()
            }
            delay(5_000)

            do {
                val targets = managementApi.getAllTargets(BASIC_AUTH).content
                val testTargets = targets.filter { devicesControllerId.contains(it.controllerId) }
                testTargets.forEach { target ->
                    println("Testing device ${target.controllerId}")
                    Assert.assertNotNull(target.lastControllerRequestAt)
                    devicesControllerId.remove(target.controllerId)
                }
                println("Waiting for ${devicesControllerId.size} remaining devices to poll ...")
                delay(1_000)
            } while (devicesControllerId.isNotEmpty())

            device.virtualMachineGlobalScope.cancel()
        }
    }
}