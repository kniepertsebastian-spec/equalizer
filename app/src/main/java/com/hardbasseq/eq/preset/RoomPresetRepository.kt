package com.hardbasseq.eq.preset

import android.util.Log
import com.hardbasseq.eq.data.db.PresetDao
import com.hardbasseq.eq.data.preset.PresetEntity
import com.hardbasseq.eq.data.preset.PresetJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RoomPresetRepository"

@Singleton
class RoomPresetRepository
    @Inject
    constructor(
        private val dao: PresetDao,
    ) : PresetRepository {
        override val customPresets: Flow<List<Preset>> =
            dao.getAllFlow().map { entities ->
                // M2 acceptance criterion: "ein beschädigtes Nutzerpreset kann die
                // App nicht am Start hindern" - one row failing PresetJsonSerializer's
                // validation (bad JSON, out-of-range gain, empty curve, ...) is
                // dropped and logged rather than crashing this flow (and with it,
                // every screen that collects it).
                entities.mapNotNull { entity ->
                    PresetJsonSerializer
                        .importFromJson(entity.presetJson)
                        .onFailure { e -> Log.w(TAG, "Skipping corrupted custom preset ${entity.id}: ${e.message}") }
                        .getOrNull()
                }
            }

        override suspend fun save(preset: Preset) {
            dao.upsert(
                PresetEntity(
                    id = preset.id,
                    name = preset.name,
                    presetJson = PresetJsonSerializer.exportToJson(preset),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun delete(id: String) {
            dao.deleteById(id)
        }
    }
