package com.impulsive.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UserProfileDao_Impl implements UserProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UserProfile> __insertionAdapterOfUserProfile;

  private final EntityDeletionOrUpdateAdapter<UserProfile> __updateAdapterOfUserProfile;

  public UserProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUserProfile = new EntityInsertionAdapter<UserProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `user_profile` (`id`,`baselineSessionsPerWeek`,`path`,`identityAnchor`,`triggers`,`onboardingComplete`,`lastSessionCompleteTimestamp`,`monitoredApps`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBaselineSessionsPerWeek());
        statement.bindString(3, entity.getPath());
        statement.bindString(4, entity.getIdentityAnchor());
        statement.bindString(5, entity.getTriggers());
        final int _tmp = entity.getOnboardingComplete() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getLastSessionCompleteTimestamp());
        statement.bindString(8, entity.getMonitoredApps());
      }
    };
    this.__updateAdapterOfUserProfile = new EntityDeletionOrUpdateAdapter<UserProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `user_profile` SET `id` = ?,`baselineSessionsPerWeek` = ?,`path` = ?,`identityAnchor` = ?,`triggers` = ?,`onboardingComplete` = ?,`lastSessionCompleteTimestamp` = ?,`monitoredApps` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBaselineSessionsPerWeek());
        statement.bindString(3, entity.getPath());
        statement.bindString(4, entity.getIdentityAnchor());
        statement.bindString(5, entity.getTriggers());
        final int _tmp = entity.getOnboardingComplete() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getLastSessionCompleteTimestamp());
        statement.bindString(8, entity.getMonitoredApps());
        statement.bindLong(9, entity.getId());
      }
    };
  }

  @Override
  public Object upsert(final UserProfile profile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfUserProfile.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final UserProfile profile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfUserProfile.handle(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<UserProfile> observe() {
    final String _sql = "SELECT * FROM user_profile WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_profile"}, new Callable<UserProfile>() {
      @Override
      @Nullable
      public UserProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBaselineSessionsPerWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineSessionsPerWeek");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfIdentityAnchor = CursorUtil.getColumnIndexOrThrow(_cursor, "identityAnchor");
          final int _cursorIndexOfTriggers = CursorUtil.getColumnIndexOrThrow(_cursor, "triggers");
          final int _cursorIndexOfOnboardingComplete = CursorUtil.getColumnIndexOrThrow(_cursor, "onboardingComplete");
          final int _cursorIndexOfLastSessionCompleteTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSessionCompleteTimestamp");
          final int _cursorIndexOfMonitoredApps = CursorUtil.getColumnIndexOrThrow(_cursor, "monitoredApps");
          final UserProfile _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpBaselineSessionsPerWeek;
            _tmpBaselineSessionsPerWeek = _cursor.getInt(_cursorIndexOfBaselineSessionsPerWeek);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpIdentityAnchor;
            _tmpIdentityAnchor = _cursor.getString(_cursorIndexOfIdentityAnchor);
            final String _tmpTriggers;
            _tmpTriggers = _cursor.getString(_cursorIndexOfTriggers);
            final boolean _tmpOnboardingComplete;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfOnboardingComplete);
            _tmpOnboardingComplete = _tmp != 0;
            final long _tmpLastSessionCompleteTimestamp;
            _tmpLastSessionCompleteTimestamp = _cursor.getLong(_cursorIndexOfLastSessionCompleteTimestamp);
            final String _tmpMonitoredApps;
            _tmpMonitoredApps = _cursor.getString(_cursorIndexOfMonitoredApps);
            _result = new UserProfile(_tmpId,_tmpBaselineSessionsPerWeek,_tmpPath,_tmpIdentityAnchor,_tmpTriggers,_tmpOnboardingComplete,_tmpLastSessionCompleteTimestamp,_tmpMonitoredApps);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object get(final Continuation<? super UserProfile> $completion) {
    final String _sql = "SELECT * FROM user_profile WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserProfile>() {
      @Override
      @Nullable
      public UserProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBaselineSessionsPerWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineSessionsPerWeek");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfIdentityAnchor = CursorUtil.getColumnIndexOrThrow(_cursor, "identityAnchor");
          final int _cursorIndexOfTriggers = CursorUtil.getColumnIndexOrThrow(_cursor, "triggers");
          final int _cursorIndexOfOnboardingComplete = CursorUtil.getColumnIndexOrThrow(_cursor, "onboardingComplete");
          final int _cursorIndexOfLastSessionCompleteTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSessionCompleteTimestamp");
          final int _cursorIndexOfMonitoredApps = CursorUtil.getColumnIndexOrThrow(_cursor, "monitoredApps");
          final UserProfile _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpBaselineSessionsPerWeek;
            _tmpBaselineSessionsPerWeek = _cursor.getInt(_cursorIndexOfBaselineSessionsPerWeek);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpIdentityAnchor;
            _tmpIdentityAnchor = _cursor.getString(_cursorIndexOfIdentityAnchor);
            final String _tmpTriggers;
            _tmpTriggers = _cursor.getString(_cursorIndexOfTriggers);
            final boolean _tmpOnboardingComplete;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfOnboardingComplete);
            _tmpOnboardingComplete = _tmp != 0;
            final long _tmpLastSessionCompleteTimestamp;
            _tmpLastSessionCompleteTimestamp = _cursor.getLong(_cursorIndexOfLastSessionCompleteTimestamp);
            final String _tmpMonitoredApps;
            _tmpMonitoredApps = _cursor.getString(_cursorIndexOfMonitoredApps);
            _result = new UserProfile(_tmpId,_tmpBaselineSessionsPerWeek,_tmpPath,_tmpIdentityAnchor,_tmpTriggers,_tmpOnboardingComplete,_tmpLastSessionCompleteTimestamp,_tmpMonitoredApps);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
