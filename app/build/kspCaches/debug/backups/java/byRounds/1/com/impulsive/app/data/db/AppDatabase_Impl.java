package com.impulsive.app.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile UserProfileDao _userProfileDao;

  private volatile TriggerLogDao _triggerLogDao;

  private volatile WeeklyTargetDao _weeklyTargetDao;

  private volatile EvalMetricsDao _evalMetricsDao;

  private volatile BypassEventDao _bypassEventDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `baselineSessionsPerWeek` INTEGER NOT NULL, `path` TEXT NOT NULL, `identityAnchor` TEXT NOT NULL, `triggers` TEXT NOT NULL, `onboardingComplete` INTEGER NOT NULL, `lastSessionCompleteTimestamp` INTEGER NOT NULL, `monitoredApps` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `trigger_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `triggerType` TEXT NOT NULL, `outcome` TEXT NOT NULL, `holdDurationSeconds` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `weekly_target` (`weekStartDate` INTEGER NOT NULL, `allowedSessions` INTEGER NOT NULL, `usedSessions` INTEGER NOT NULL, `stallReason` TEXT NOT NULL, PRIMARY KEY(`weekStartDate`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `eval_metrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phaseNumber` INTEGER NOT NULL, `metricName` TEXT NOT NULL, `metricValue` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `bypass_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL, `recovered` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '67f880f290a2f6efb0bd1be775832d1c')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `user_profile`");
        db.execSQL("DROP TABLE IF EXISTS `trigger_log`");
        db.execSQL("DROP TABLE IF EXISTS `weekly_target`");
        db.execSQL("DROP TABLE IF EXISTS `eval_metrics`");
        db.execSQL("DROP TABLE IF EXISTS `bypass_event`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsUserProfile = new HashMap<String, TableInfo.Column>(8);
        _columnsUserProfile.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("baselineSessionsPerWeek", new TableInfo.Column("baselineSessionsPerWeek", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("path", new TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("identityAnchor", new TableInfo.Column("identityAnchor", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("triggers", new TableInfo.Column("triggers", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("onboardingComplete", new TableInfo.Column("onboardingComplete", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("lastSessionCompleteTimestamp", new TableInfo.Column("lastSessionCompleteTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("monitoredApps", new TableInfo.Column("monitoredApps", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfile = new TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile);
        final TableInfo _existingUserProfile = TableInfo.read(db, "user_profile");
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profile(com.impulsive.app.data.db.UserProfile).\n"
                  + " Expected:\n" + _infoUserProfile + "\n"
                  + " Found:\n" + _existingUserProfile);
        }
        final HashMap<String, TableInfo.Column> _columnsTriggerLog = new HashMap<String, TableInfo.Column>(5);
        _columnsTriggerLog.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTriggerLog.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTriggerLog.put("triggerType", new TableInfo.Column("triggerType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTriggerLog.put("outcome", new TableInfo.Column("outcome", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTriggerLog.put("holdDurationSeconds", new TableInfo.Column("holdDurationSeconds", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTriggerLog = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTriggerLog = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTriggerLog = new TableInfo("trigger_log", _columnsTriggerLog, _foreignKeysTriggerLog, _indicesTriggerLog);
        final TableInfo _existingTriggerLog = TableInfo.read(db, "trigger_log");
        if (!_infoTriggerLog.equals(_existingTriggerLog)) {
          return new RoomOpenHelper.ValidationResult(false, "trigger_log(com.impulsive.app.data.db.TriggerLog).\n"
                  + " Expected:\n" + _infoTriggerLog + "\n"
                  + " Found:\n" + _existingTriggerLog);
        }
        final HashMap<String, TableInfo.Column> _columnsWeeklyTarget = new HashMap<String, TableInfo.Column>(4);
        _columnsWeeklyTarget.put("weekStartDate", new TableInfo.Column("weekStartDate", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeeklyTarget.put("allowedSessions", new TableInfo.Column("allowedSessions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeeklyTarget.put("usedSessions", new TableInfo.Column("usedSessions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeeklyTarget.put("stallReason", new TableInfo.Column("stallReason", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWeeklyTarget = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWeeklyTarget = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWeeklyTarget = new TableInfo("weekly_target", _columnsWeeklyTarget, _foreignKeysWeeklyTarget, _indicesWeeklyTarget);
        final TableInfo _existingWeeklyTarget = TableInfo.read(db, "weekly_target");
        if (!_infoWeeklyTarget.equals(_existingWeeklyTarget)) {
          return new RoomOpenHelper.ValidationResult(false, "weekly_target(com.impulsive.app.data.db.WeeklyTarget).\n"
                  + " Expected:\n" + _infoWeeklyTarget + "\n"
                  + " Found:\n" + _existingWeeklyTarget);
        }
        final HashMap<String, TableInfo.Column> _columnsEvalMetrics = new HashMap<String, TableInfo.Column>(5);
        _columnsEvalMetrics.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvalMetrics.put("phaseNumber", new TableInfo.Column("phaseNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvalMetrics.put("metricName", new TableInfo.Column("metricName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvalMetrics.put("metricValue", new TableInfo.Column("metricValue", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvalMetrics.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEvalMetrics = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEvalMetrics = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEvalMetrics = new TableInfo("eval_metrics", _columnsEvalMetrics, _foreignKeysEvalMetrics, _indicesEvalMetrics);
        final TableInfo _existingEvalMetrics = TableInfo.read(db, "eval_metrics");
        if (!_infoEvalMetrics.equals(_existingEvalMetrics)) {
          return new RoomOpenHelper.ValidationResult(false, "eval_metrics(com.impulsive.app.data.db.EvalMetrics).\n"
                  + " Expected:\n" + _infoEvalMetrics + "\n"
                  + " Found:\n" + _existingEvalMetrics);
        }
        final HashMap<String, TableInfo.Column> _columnsBypassEvent = new HashMap<String, TableInfo.Column>(4);
        _columnsBypassEvent.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBypassEvent.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBypassEvent.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBypassEvent.put("recovered", new TableInfo.Column("recovered", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBypassEvent = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBypassEvent = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBypassEvent = new TableInfo("bypass_event", _columnsBypassEvent, _foreignKeysBypassEvent, _indicesBypassEvent);
        final TableInfo _existingBypassEvent = TableInfo.read(db, "bypass_event");
        if (!_infoBypassEvent.equals(_existingBypassEvent)) {
          return new RoomOpenHelper.ValidationResult(false, "bypass_event(com.impulsive.app.data.db.BypassEvent).\n"
                  + " Expected:\n" + _infoBypassEvent + "\n"
                  + " Found:\n" + _existingBypassEvent);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "67f880f290a2f6efb0bd1be775832d1c", "5f6019ce237213e647036da429f6bf15");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "user_profile","trigger_log","weekly_target","eval_metrics","bypass_event");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `user_profile`");
      _db.execSQL("DELETE FROM `trigger_log`");
      _db.execSQL("DELETE FROM `weekly_target`");
      _db.execSQL("DELETE FROM `eval_metrics`");
      _db.execSQL("DELETE FROM `bypass_event`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(UserProfileDao.class, UserProfileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TriggerLogDao.class, TriggerLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WeeklyTargetDao.class, WeeklyTargetDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EvalMetricsDao.class, EvalMetricsDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BypassEventDao.class, BypassEventDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public UserProfileDao userProfileDao() {
    if (_userProfileDao != null) {
      return _userProfileDao;
    } else {
      synchronized(this) {
        if(_userProfileDao == null) {
          _userProfileDao = new UserProfileDao_Impl(this);
        }
        return _userProfileDao;
      }
    }
  }

  @Override
  public TriggerLogDao triggerLogDao() {
    if (_triggerLogDao != null) {
      return _triggerLogDao;
    } else {
      synchronized(this) {
        if(_triggerLogDao == null) {
          _triggerLogDao = new TriggerLogDao_Impl(this);
        }
        return _triggerLogDao;
      }
    }
  }

  @Override
  public WeeklyTargetDao weeklyTargetDao() {
    if (_weeklyTargetDao != null) {
      return _weeklyTargetDao;
    } else {
      synchronized(this) {
        if(_weeklyTargetDao == null) {
          _weeklyTargetDao = new WeeklyTargetDao_Impl(this);
        }
        return _weeklyTargetDao;
      }
    }
  }

  @Override
  public EvalMetricsDao evalMetricsDao() {
    if (_evalMetricsDao != null) {
      return _evalMetricsDao;
    } else {
      synchronized(this) {
        if(_evalMetricsDao == null) {
          _evalMetricsDao = new EvalMetricsDao_Impl(this);
        }
        return _evalMetricsDao;
      }
    }
  }

  @Override
  public BypassEventDao bypassEventDao() {
    if (_bypassEventDao != null) {
      return _bypassEventDao;
    } else {
      synchronized(this) {
        if(_bypassEventDao == null) {
          _bypassEventDao = new BypassEventDao_Impl(this);
        }
        return _bypassEventDao;
      }
    }
  }
}
