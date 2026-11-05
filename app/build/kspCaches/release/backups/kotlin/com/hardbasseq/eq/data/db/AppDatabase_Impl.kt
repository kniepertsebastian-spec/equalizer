package com.hardbasseq.eq.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _presetDao: Lazy<PresetDao> = lazy {
    PresetDao_Impl(this)
  }

  private val _deviceProfileDao: Lazy<DeviceProfileDao> = lazy {
    DeviceProfileDao_Impl(this)
  }

  private val _correctionProfileDao: Lazy<CorrectionProfileDao> = lazy {
    CorrectionProfileDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2,
        "4d0fc6c4b6d29e9c9506958467d115ff", "92020027ae9e9be3a353350c0f9c62d4") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `custom_presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `presetJson` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `device_profiles` (`routeId` TEXT NOT NULL, `routeType` TEXT NOT NULL, `displayName` TEXT NOT NULL, `boundPresetId` TEXT NOT NULL, `boundCorrectionProfileId` TEXT NOT NULL, `headphoneAcousticsOverride` INTEGER, `contextMode` TEXT, PRIMARY KEY(`routeId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `custom_correction_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `profileJson` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4d0fc6c4b6d29e9c9506958467d115ff')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `custom_presets`")
        connection.execSQL("DROP TABLE IF EXISTS `device_profiles`")
        connection.execSQL("DROP TABLE IF EXISTS `custom_correction_profiles`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsCustomPresets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCustomPresets.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomPresets.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomPresets.put("presetJson", TableInfo.Column("presetJson", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomPresets.put("updatedAtMillis", TableInfo.Column("updatedAtMillis", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCustomPresets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCustomPresets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCustomPresets: TableInfo = TableInfo("custom_presets", _columnsCustomPresets,
            _foreignKeysCustomPresets, _indicesCustomPresets)
        val _existingCustomPresets: TableInfo = read(connection, "custom_presets")
        if (!_infoCustomPresets.equals(_existingCustomPresets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |custom_presets(com.hardbasseq.eq.data.preset.PresetEntity).
              | Expected:
              |""".trimMargin() + _infoCustomPresets + """
              |
              | Found:
              |""".trimMargin() + _existingCustomPresets)
        }
        val _columnsDeviceProfiles: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDeviceProfiles.put("routeId", TableInfo.Column("routeId", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("routeType", TableInfo.Column("routeType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("displayName", TableInfo.Column("displayName", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("boundPresetId", TableInfo.Column("boundPresetId", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("boundCorrectionProfileId",
            TableInfo.Column("boundCorrectionProfileId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("headphoneAcousticsOverride",
            TableInfo.Column("headphoneAcousticsOverride", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDeviceProfiles.put("contextMode", TableInfo.Column("contextMode", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDeviceProfiles: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDeviceProfiles: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDeviceProfiles: TableInfo = TableInfo("device_profiles", _columnsDeviceProfiles,
            _foreignKeysDeviceProfiles, _indicesDeviceProfiles)
        val _existingDeviceProfiles: TableInfo = read(connection, "device_profiles")
        if (!_infoDeviceProfiles.equals(_existingDeviceProfiles)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |device_profiles(com.hardbasseq.eq.data.profile.DeviceProfileEntity).
              | Expected:
              |""".trimMargin() + _infoDeviceProfiles + """
              |
              | Found:
              |""".trimMargin() + _existingDeviceProfiles)
        }
        val _columnsCustomCorrectionProfiles: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCustomCorrectionProfiles.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomCorrectionProfiles.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomCorrectionProfiles.put("profileJson", TableInfo.Column("profileJson", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCustomCorrectionProfiles.put("updatedAtMillis", TableInfo.Column("updatedAtMillis",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCustomCorrectionProfiles: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCustomCorrectionProfiles: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCustomCorrectionProfiles: TableInfo = TableInfo("custom_correction_profiles",
            _columnsCustomCorrectionProfiles, _foreignKeysCustomCorrectionProfiles,
            _indicesCustomCorrectionProfiles)
        val _existingCustomCorrectionProfiles: TableInfo = read(connection,
            "custom_correction_profiles")
        if (!_infoCustomCorrectionProfiles.equals(_existingCustomCorrectionProfiles)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |custom_correction_profiles(com.hardbasseq.eq.data.correction.CorrectionProfileEntity).
              | Expected:
              |""".trimMargin() + _infoCustomCorrectionProfiles + """
              |
              | Found:
              |""".trimMargin() + _existingCustomCorrectionProfiles)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "custom_presets",
        "device_profiles", "custom_correction_profiles")
  }

  public override fun clearAllTables() {
    super.performClear(false, "custom_presets", "device_profiles", "custom_correction_profiles")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(PresetDao::class, PresetDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DeviceProfileDao::class, DeviceProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CorrectionProfileDao::class,
        CorrectionProfileDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun presetDao(): PresetDao = _presetDao.value

  public override fun deviceProfileDao(): DeviceProfileDao = _deviceProfileDao.value

  public override fun correctionProfileDao(): CorrectionProfileDao = _correctionProfileDao.value
}
