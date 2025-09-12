/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.virtualdevice

import okhttp3.Response
import okio.Sink
import okio.Source
import org.joda.time.Duration
import java.net.Socket
import java.util.Properties
import java.util.UUID

data class Configuration(

    val logLevel: String,

    /**
     * Number of virtual device generated
     */
    val poolSize: Int,

    val tenant: String,

    val controllerIdGenerator: (Int) -> String,

    val url: String,

    val gatewayToken: String,

    /**
     * Each virtual device will be started with a random delay in [0, HARA_VIRTUAL_DEVICE_STARTING_DELAY]
     */
    val virtualDeviceStartingDelay: Long,

    val storagePath: String,

    /**
     * A list of target attributes for each device.
     *
     * The string must have the form of: key1,value1|key2,value2|....|keyn,valuen
     *
     * The value supports the following template substitutions:
     * {0} is replaced with the virtual device id
     * {1} is replaced with the tenant
     * {2} is replaced with the controller id
     * {3} is replaced with the gatewayToken
     *
     * Example:
     *  the string "virtual_device_id,{0}|virtual_device_controller_id,{1}|client,kotlin" represents
     *  three target attributes:
     *  1- virtual_device_id = 7
     *  2- virtual_device_controller_id = a18b68b4-4c9b-4c8f-91af-a26d3a2d1008
     *  3- client = kotlin
     *
     */
    val targetAttributes: String,

    /**
     *
     * Template substitutions:
     * {0} is replaced with the virtual device id
     * {1} is replaced with the tenant
     * {2} is replaced with the controller id
     * {3} is replaced with the gatewayToken
     * {4} is replaced with the message
     *
     */
    val logMessageTemplate: String,

    /**
     *
     * Template substitutions:
     * {0} is replaced with the software module's name
     * {1} is replaced with the virtual device's id
     * {2} is replaced with the tenant
     * {3} is replaced with the controller id
     * {4} is replaced with the gatewayToken
     *
     */
    val srvMsgTemplateBeforeUpdate: String,

    /**
     *
     * Template substitutions:
     * {0} is replaced with the software module's name
     * {1} is replaced with the virtual device's id
     * {2} is replaced with the tenant
     * {3} is replaced with the controller id
     * {4} is replaced with the gatewayToken
     *
     */
    val srvMsgTemplateAfterUpdate: String,

    val grantDownload: Boolean,

    val grantUpdate: Boolean,

    /**
     * Sets the default connect timeout for new connections in seconds.
     * A value of 0 means no timeout, otherwise values must bebetween 1 and Integer.MAX_VALUE when converted to
     * milliseconds.
     * The connect timeout is applied when connecting a TCP socket to the target host.
     * The default value is 10 seconds.
     */
    val connectTimeout: Long,

    /**
     * Sets the default timeout for complete calls in seconds.
     * A value of 0 means no timeout, otherwise values must be between 1 and Integer.MAX_VALUE when converted to
     * milliseconds.
     * The call timeout spans the entire call: resolving DNS, connecting, writing the request body, server processing,
     * and reading the response body. If the call requires redirects or retries all must complete within one timeout
     * period.
     * The default value is 0 which imposes no timeout.
     */
    val callTimeout: Long,

    /**
     * Sets the default read timeout for new connections. A value of 0 means no timeout, otherwise
     * values must be between 1 and [Integer.MAX_VALUE] when converted to milliseconds.
     *
     * The read timeout is applied to both the TCP socket and for individual read IO operations
     * including on [Source] of the [Response]. The default value is 10 seconds.
     *
     * @see Socket.setSoTimeout
     * @see Source.timeout
     */
    val readTimeout: Long,

    /**
     * Sets the default write timeout for new connections. A value of 0 means no timeout, otherwise
     * values must be between 1 and [Integer.MAX_VALUE] when converted to milliseconds.
     *
     * The write timeout is applied for individual write IO operations. The default value is 10
     * seconds.
     *
     * @see Sink.timeout
     */
    val writeTimeout: Long
) {
    companion object {

        fun default(): Configuration {

            val propertiesFromFile = Configuration::class.java.classLoader
                .getResourceAsStream("application.properties")?.use { inputStream ->
                    Properties().apply { load(inputStream) }
                }

            fun property(prop: String): String? {
                val properties = propertiesFromFile?.apply { putAll(System.getProperties()) } ?: System.getProperties()
                val envName = prop.uppercase().replace(".", "_")
                return properties.getProperty(prop)?.let { propValue ->
                    if (propValue.trim() == "\${${envName}}") System.getenv(envName)
                    else propValue
                }
            }

            fun property(prop: String, defaultValue: String): String {
                return property(prop) ?: defaultValue
            }

            return Configuration(
                property("virtdevice.log.level", "TRACE"),
                property("virtdevice.client.pool.size", "1").toInt(),
                property("virtdevice.hawkbit.tenant", "DEFAULT"),
                { id -> property("virtdevice.hawkbit.controller.id")
                    ?.let { "${it}_$id" } ?: UUID.randomUUID().toString() },
                property("virtdevice.hawkbit.url", "http://localhost:8080"),
                property("virtdevice.hawkbit.gateway.token", ""),
                Duration.standardSeconds(
                    property("virtdevice.starting.delay", "1").toLong()).millis,
                property("virtdevice.storage.path", "/client"),
                property("virtdevice.target.attributes", "client,kotlin virtual device"),
                property("virtdevice.log.message", "{4}"),
                property("virtdevice.srv.msg.before.update", "Applying the sw {0} for target {1}"),
                property("virtdevice.srv.msg.after.update", "Applied the sw {0} for target {1}"),
                property("virtdevice.grant.download", "true").toBoolean(),
                property("virtdevice.grant.update", "true").toBoolean(),
                property("virtdevice.connect.timeout", "10").toLong(),
                property("virtdevice.call.timeout", "0").toLong(),
                property("virtdevice.read.timeout", "10").toLong(),
                property("virtdevice.write.timeout", "10").toLong()
            )
        }
    }
}