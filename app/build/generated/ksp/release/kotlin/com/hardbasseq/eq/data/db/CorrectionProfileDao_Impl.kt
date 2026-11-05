package com.hardbasseq.eq.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.hardbasseq.eq.`data`.correction.CorrectionProfileEntity
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
public class CorrectionProfileDao_Impl(
  __db: RoomDatabase,
) : CorrectionProfileDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCorrectionProfileEntity: EntityInsertAdapter<CorrectionProfileEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCorrectionProfileEntity = object :
        EntityInsertAdapter<CorrectionProfileEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `custom_correction_profiles` (`id`,`name`,`profileJson`,`updatedAtMillis`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CorrectionProfileEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.profileJson)
        statement.bindLong(4, entity.updatedAtMillis)
      }
    }
  }

  public override suspend fun upsert(entity: CorrectionProfileEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCorrectionProfileEntity.insert(_connection, entity)
  }

  public override fun getAllFlow(): Flow<List<CorrectionProfileEntity>> {
    val _sql: String = "SELECT * FROM custom_correction_profiles ORDER BY updatedAtMillis DESC"
    return createFlow(__db, false, arrayOf("custom_correction_profiles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfProfileJson: Int = getColumnIndexOrThrow(_stmt, "profileJson")
        val _columnIndexOfUpdatedAtMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtMillis")
        val _result: MutableList<CorrectionProfileEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CorrectionProfileEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpProfileJson: String
          _tmpProfileJson = _stmt.getText(_columnIndexOfProfileJson)
          val _tmpUpdatedAtMillis: Long
          _tmpUpdatedAtMillis = _stmt.getLong(_columnIndexOfUpdatedAtMillis)
          _item = CorrectionProfileEntity(_tmpId,_tmpName,_tmpProfileJson,_tmpUpdatedAtMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM custom_correction_profiles WHERE id = ?"
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
