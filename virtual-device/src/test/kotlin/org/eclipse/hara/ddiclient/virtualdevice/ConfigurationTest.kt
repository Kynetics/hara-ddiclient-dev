package org.eclipse.hara.ddiclient.virtualdevice

import org.testng.Assert.assertEquals
import org.testng.annotations.AfterTest
import org.testng.annotations.Test

class ConfigurationTest {

    private val originalProperties = System.getProperties()
    
    @AfterTest
    fun restore() {
        // Reset System Properties before each test
        clearSystemProperties()
        System.setProperties(originalProperties)
    }

    @Test
    fun `should load default values when no properties or environment variables are set`() {
        // Given: no property or environment variable set
        clearSystemProperties()
        
        // When: create a new configuration
        val config = Configuration.default()
        
        // Then: should use default values
        assertEquals(config.logLevel, "TRACE")
        assertEquals(config.poolSize, 1)
        assertEquals(config.tenant, "DEFAULT")
        assertEquals(config.url, "http://localhost:8080")
        assertEquals(config.gatewayToken, "")
        assertEquals(config.storagePath, "/client")
        assertEquals(config.targetAttributes, "client,kotlin virtual device")
        assertEquals(config.logMessageTemplate, "{4}")
        assertEquals(config.srvMsgTemplateBeforeUpdate, "Applying the sw {0} for target {1}")
        assertEquals(config.srvMsgTemplateAfterUpdate, "Applied the sw {0} for target {1}")
        assertEquals(config.grantDownload, true)
        assertEquals(config.grantUpdate, true)
        assertEquals(config.connectTimeout, 10L)
        assertEquals(config.callTimeout, 0L)
        assertEquals(config.readTimeout, 10L)
        assertEquals(config.writeTimeout, 10L)
    }


    @Test
    fun `should prioritize system properties over application properties`() {
        // Given: System Properties set
        System.setProperty("virtdevice.log.level", "INFO")
        System.setProperty("virtdevice.client.pool.size", "3")
        System.setProperty("virtdevice.hawkbit.tenant", "SYSTEM_TENANT")
        
        // When: create a new configuration
        val config = Configuration.default()
        
        // Then: should use the values from System Properties
        assertEquals(config.logLevel, "INFO")
        assertEquals(config.poolSize, 3)
        assertEquals(config.tenant, "SYSTEM_TENANT")
    }

    @Test
    fun `should handle boolean properties correctly`() {
        // Given: System Properties for boolean values
        System.setProperty("virtdevice.grant.download", "false")
        System.setProperty("virtdevice.grant.update", "true")
        
        // When: create a new configuration
        val config = Configuration.default()
        
        // Then: should correctly convert boolean values
        assertEquals(config.grantDownload, false)
        assertEquals(config.grantUpdate, true)
    }

    @Test
    fun `should handle numeric properties correctly`() {
        // Given: System Properties for numeric values
        System.setProperty("virtdevice.client.pool.size", "15")
        System.setProperty("virtdevice.connect.timeout", "60")
        System.setProperty("virtdevice.call.timeout", "120")
        
        // When: create a new configuration
        val config = Configuration.default()
        
        // Then: should correctly convert numeric values
        assertEquals(config.poolSize, 15)
        assertEquals(config.connectTimeout, 60L)
        assertEquals(config.callTimeout, 120L)
    }

    @Test
    fun `should handle controller id generator correctly`() {
        // Given: System Property for controller ID
        System.setProperty("virtdevice.hawkbit.controller.id", "TEST_CONTROLLER")
        
        // When: create a new configuration and generate an ID
        val config = Configuration.default()
        val generatedId = config.controllerIdGenerator(1)
        
        // Then: should use the template with the virtual ID
        assertEquals(generatedId, "TEST_CONTROLLER_1")
    }

    @Test
    fun `should generate UUID when controller id is not set`() {
        // Given: no controller ID set
        clearSystemProperties()
        
        // When: create a new configuration and generate an ID
        val config = Configuration.default()
        val generatedId = config.controllerIdGenerator(1)
        
        // Then: should generate a UUID
        assert(generatedId.matches(Regex("[0-9a-f-]{36}"))) {
            "Generated ID should be a valid UUID format, but was: $generatedId"
        }
    }

    private fun clearSystemProperties() {
        // Removes all System Properties that start with "virtdevice."
        System.getProperties().keys.toList()
            .filterIsInstance<String>()
            .filter { it.startsWith("virtdevice.") }
            .forEach { System.clearProperty(it) }
    }
}