package com.hardbasseq.eq.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.hardbasseq.eq.`data`.preset.PresetEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PresetDao_Impl(
  __db: RoomDatabase,
) : PresetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPresetEntity: EntityInsertAdapter<PresetEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPresetEntity = object : EntityInsertAdapter<PresetEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `custom_presets` (`id`,`name`,`presetJson`,`updatedAtMillis`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PresetEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.presetJson)
        statement.bindLong(4, entity.updatedAtMillis)
      }
    }
  }

  public override suspend fun upsert(entity: PresetEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPresetEntity.insert(_connection, entity)
  }

  public override fun getAllFlow(): Flow<List<PresetEntity>> {
    val _sql: String = "SELECT * FROM custom_presets ORDER BY updatedAtMillis DESC"
    return createFlow(__db, false, arrayOf("custom_presets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPresetJson: Int = getColumnIndexOrThrow(_stmt, "presetJson")
        val _columnIndexOfUpdatedAtMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtMillis")
        val _result: MutableList<PresetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PresetEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPresetJson: String
          _tmpPresetJson = _stmt.getText(_columnIndexOfPresetJson)
          val _tmpUpdatedAtMillis: Long
          _tmpUpdatedAtMillis = _stmt.getLong(_columnIndexOfUpdatedAtMillis)
          _item = PresetEntity(_tmpId,_tmpName,_tmpPresetJson,_tmpUpdatedAtMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM custom_presets WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
