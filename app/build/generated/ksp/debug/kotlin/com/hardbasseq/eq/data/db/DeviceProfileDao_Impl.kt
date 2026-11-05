package com.hardbasseq.eq.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.hardbasseq.eq.`data`.profile.DeviceProfileEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DeviceProfileDao_Impl(
  __db: RoomDatabase,
) : DeviceProfileDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDeviceProfileEntity: EntityInsertAdapter<DeviceProfileEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDeviceProfileEntity = object : EntityInsertAdapter<DeviceProfileEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `device_profiles` (`routeId`,`routeType`,`displayName`,`boundPresetId`,`boundCorrectionProfileId`,`headphoneAcousticsOverride`,`contextMode`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DeviceProfileEntity) {
        statement.bindText(1, entity.routeId)
        statement.bindText(2, entity.routeType)
        statement.bindText(3, entity.displayName)
        statement.bindText(4, entity.boundPresetId)
        statement.bindText(5, entity.boundCorrectionProfileId)
        val _tmpHeadphoneAcousticsOverride: Boolean? = entity.headphoneAcousticsOverride
        val _tmp: Int? = _tmpHeadphoneAcousticsOverride?.let { if (it) 1 else 0 }
        if (_tmp == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmp.toLong())
        }
        val _tmpContextMode: String? = entity.contextMode
        if (_tmpContextMode == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpContextMode)
        }
      }
    }
  }

  public override suspend fun saveProfile(profile: DeviceProfileEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfDeviceProfileEntity.insertAndReturnId(_connection, profile)
    _result
  }

  public override suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity? {
    val _sql: String = "SELECT * FROM device_profiles WHERE routeId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, routeId)
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfRouteType: Int = getColumnIndexOrThrow(_stmt, "routeType")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfBoundPresetId: Int = getColumnIndexOrThrow(_stmt, "boundPresetId")
        val _columnIndexOfBoundCorrectionProfileId: Int = getColumnIndexOrThrow(_stmt, "boundCorrectionProfileId")
        val _columnIndexOfHeadphoneAcousticsOverride: Int = getColumnIndexOrThrow(_stmt, "headphoneAcousticsOverride")
        val _columnIndexOfContextMode: Int = getColumnIndexOrThrow(_stmt, "contextMode")
        val _result: DeviceProfileEntity?
        if (_stmt.step()) {
          val _tmpRouteId: String
          _tmpRouteId = _stmt.getText(_columnIndexOfRouteId)
          val _tmpRouteType: String
          _tmpRouteType = _stmt.getText(_columnIndexOfRouteType)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpBoundPresetId: String
          _tmpBoundPresetId = _stmt.getText(_columnIndexOfBoundPresetId)
          val _tmpBoundCorrectionProfileId: String
          _tmpBoundCorrectionProfileId = _stmt.getText(_columnIndexOfBoundCorrectionProfileId)
          val _tmpHeadphoneAcousticsOverride: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfHeadphoneAcousticsOverride)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfHeadphoneAcousticsOverride).toInt()
          }
          _tmpHeadphoneAcousticsOverride = _tmp?.let { it != 0 }
          val _tmpContextMode: String?
          if (_stmt.isNull(_columnIndexOfContextMode)) {
            _tmpContextMode = null
          } else {
            _tmpContextMode = _stmt.getText(_columnIndexOfContextMode)
          }
          _result = DeviceProfileEntity(_tmpRouteId,_tmpRouteType,_tmpDisplayName,_tmpBoundPresetId,_tmpBoundCorrectionProfileId,_tmpHeadphoneAcousticsOverride,_tmpContextMode)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllProfilesFlow(): Flow<List<DeviceProfileEntity>> {
    val _sql: String = "SELECT * FROM device_profiles"
    return createFlow(__db, false, arrayOf("device_profiles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfRouteType: Int = getColumnIndexOrThrow(_stmt, "routeType")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfBoundPresetId: Int = getColumnIndexOrThrow(_stmt, "boundPresetId")
        val _columnIndexOfBoundCorrectionProfileId: Int = getColumnIndexOrThrow(_stmt, "boundCorrectionProfileId")
        val _columnIndexOfHeadphoneAcousticsOverride: Int = getColumnIndexOrThrow(_stmt, "headphoneAcousticsOverride")
        val _columnIndexOfContextMode: Int = getColumnIndexOrThrow(_stmt, "contextMode")
        val _result: MutableList<DeviceProfileEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeviceProfileEntity
          val _tmpRouteId: String
          _tmpRouteId = _stmt.getText(_columnIndexOfRouteId)
          val _tmpRouteType: String
          _tmpRouteType = _stmt.getText(_columnIndexOfRouteType)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpBoundPresetId: String
          _tmpBoundPresetId = _stmt.getText(_columnIndexOfBoundPresetId)
          val _tmpBoundCorrectionProfileId: String
          _tmpBoundCorrectionProfileId = _stmt.getText(_columnIndexOfBoundCorrectionProfileId)
          val _tmpHeadphoneAcousticsOverride: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfHeadphoneAcousticsOverride)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfHeadphoneAcousticsOverride).toInt()
          }
          _tmpHeadphoneAcousticsOverride = _tmp?.let { it != 0 }
          val _tmpContextMode: String?
          if (_stmt.isNull(_columnIndexOfContextMode)) {
            _tmpContextMode = null
          } else {
            _tmpContextMode = _stmt.getText(_columnIndexOfContextMode)
          }
          _item = DeviceProfileEntity(_tmpRouteId,_tmpRouteType,_tmpDisplayName,_tmpBoundPresetId,_tmpBoundCorrectionProfileId,_tmpHeadphoneAcousticsOverride,_tmpContextMode)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
