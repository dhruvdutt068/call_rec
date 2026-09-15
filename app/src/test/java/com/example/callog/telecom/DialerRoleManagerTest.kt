package com.example.callog.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.telecom.TelecomManager
import com.example.callog.domain.service.DialerRoleManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class DialerRoleManagerTest {

    private lateinit var dialerRoleManager: DialerRoleManager
    private lateinit var fakeContext: Context

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"

            override fun getSystemService(name: String): Any? {
                if (name == Context.TELECOM_SERVICE) {
                    return Proxy.newProxyInstance(
                        TelecomManager::class.java.classLoader,
                        arrayOf()
                    ) { _, method, _ ->
                        if (method.name == "getDefaultDialerPackage") {
                            "com.example.callog"
                        } else null
                    }
                }
                return null
            }
        }

        dialerRoleManager = DialerRoleManager(fakeContext)
    }

    @Test
    fun testIsRoleAvailable_returnsTrueByDefault() {
        assertTrue(dialerRoleManager.isRoleAvailable())
    }

    @Test
    fun testIsRoleHeld_initialStateObservable() {
        val isHeld = dialerRoleManager.refreshRoleStatus()
        assertEquals(isHeld, dialerRoleManager.isDefaultDialer.value)
    }

    @Test
    fun testCreateActivityResultContract() {
        val contract = dialerRoleManager.createActivityResultContract()
        assertNotNull(contract)
    }
}
