package com.hardbasseq.eq.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAliasStoreTest {
    @Test
    fun setAlias_storesTrimmedNamePerRoute() {
        val store = InMemoryDeviceAliasStore()
        store.setAlias("bt_1", "  My headphones ")
        store.setAlias("bt_2", "Car")
        assertEquals(mapOf("bt_1" to "My headphones", "bt_2" to "Car"), store.aliases.value)
    }

    @Test
    fun blankName_removesTheAlias() {
        val store = InMemoryDeviceAliasStore()
        store.setAlias("bt_1", "My headphones")
        store.setAlias("bt_1", "   ")
        assertEquals(emptyMap<String, String>(), store.aliases.value)
    }
}
