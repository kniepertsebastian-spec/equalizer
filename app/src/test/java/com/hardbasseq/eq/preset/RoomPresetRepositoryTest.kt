package com.hardbasseq.eq.preset

import com.hardbasseq.eq.data.db.PresetDao
import com.hardbasseq.eq.data.preset.PresetEntity
import com.hardbasseq.eq.data.preset.PresetJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPresetRepositoryTest {
    @Test
    fun `a corrupted row is skipped instead of failing the whole flow`() =
        runTest {
            val validPreset = BuiltInPresets.CleanPunch.copy(id = "valid", name = "Valid")
            val dao =
                FakePresetDao(
                    initial =
                        listOf(
                            validEntity(id = "valid", name = "Valid", preset = validPreset, updatedAtMillis = 1),
                            PresetEntity(id = "broken", name = "Broken", presetJson = "not valid json at all", updatedAtMillis = 2),
                        ),
                )
            val repository = RoomPresetRepository(dao)

            val result = repository.customPresets.first()

            assertEquals(listOf("valid"), result.map { it.id })
        }

    @Test
    fun `save writes a validated JSON blob the dao can read back`() =
        runTest {
            val dao = FakePresetDao()
            val repository = RoomPresetRepository(dao)
            val preset =
                BuiltInPresets.DeepRumble.copy(
                    id = "custom-1",
                    name = "My Custom",
                    metadata = BuiltInPresets.DeepRumble.metadata.copy(builtIn = false),
                )

            repository.save(preset)

            val result = repository.customPresets.first()
            assertEquals(1, result.size)
            assertEquals("My Custom", result.single().name)
            assertFalse(result.single().metadata.builtIn)
        }

    @Test
    fun `delete removes the row`() =
        runTest {
            val gone = BuiltInPresets.CleanPunch.copy(id = "gone")
            val dao = FakePresetDao(initial = listOf(validEntity(id = "gone", name = "Gone", preset = gone, updatedAtMillis = 1)))
            val repository = RoomPresetRepository(dao)

            repository.delete("gone")

            assertTrue(repository.customPresets.first().isEmpty())
        }

    private fun validEntity(
        id: String,
        name: String,
        preset: Preset,
        updatedAtMillis: Long,
    ) = PresetEntity(id = id, name = name, presetJson = PresetJsonSerializer.exportToJson(preset), updatedAtMillis = updatedAtMillis)

    private class FakePresetDao(
        initial: List<PresetEntity> = emptyList(),
    ) : PresetDao {
        private val entities = MutableStateFlow(initial)

        override fun getAllFlow(): Flow<List<PresetEntity>> = entities

        override suspend fun upsert(entity: PresetEntity) {
            entities.value = entities.value.filterNot { it.id == entity.id } + entity
        }

        override suspend fun deleteById(id: String) {
            entities.value = entities.value.filterNot { it.id == id }
        }
    }
}
