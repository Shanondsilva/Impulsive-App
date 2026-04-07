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
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class WeeklyTargetDao_Impl implements WeeklyTargetDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WeeklyTarget> __insertionAdapterOfWeeklyTarget;

  private final EntityInsertionAdapter<WeeklyTarget> __insertionAdapterOfWeeklyTarget_1;

  private final EntityDeletionOrUpdateAdapter<WeeklyTarget> __updateAdapterOfWeeklyTarget;

  private final SharedSQLiteStatement __preparedStmtOfIncrementUsed;

  public WeeklyTargetDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWeeklyTarget = new EntityInsertionAdapter<WeeklyTarget>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `weekly_target` (`weekStartDate`,`allowedSessions`,`usedSessions`,`stallReason`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WeeklyTarget entity) {
        statement.bindLong(1, entity.getWeekStartDate());
        statement.bindLong(2, entity.getAllowedSessions());
        statement.bindLong(3, entity.getUsedSessions());
        statement.bindString(4, entity.getStallReason());
      }
    };
    this.__insertionAdapterOfWeeklyTarget_1 = new EntityInsertionAdapter<WeeklyTarget>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `weekly_target` (`weekStartDate`,`allowedSessions`,`usedSessions`,`stallReason`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WeeklyTarget entity) {
        statement.bindLong(1, entity.getWeekStartDate());
        statement.bindLong(2, entity.getAllowedSessions());
        statement.bindLong(3, entity.getUsedSessions());
        statement.bindString(4, entity.getStallReason());
      }
    };
    this.__updateAdapterOfWeeklyTarget = new EntityDeletionOrUpdateAdapter<WeeklyTarget>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `weekly_target` SET `weekStartDate` = ?,`allowedSessions` = ?,`usedSessions` = ?,`stallReason` = ? WHERE `weekStartDate` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WeeklyTarget entity) {
        statement.bindLong(1, entity.getWeekStartDate());
        statement.bindLong(2, entity.getAllowedSessions());
        statement.bindLong(3, entity.getUsedSessions());
        statement.bindString(4, entity.getStallReason());
        statement.bindLong(5, entity.getWeekStartDate());
      }
    };
    this.__preparedStmtOfIncrementUsed = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE weekly_target SET usedSessions = usedSessions + 1 WHERE weekStartDate = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final WeeklyTarget target, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfWeeklyTarget.insert(target);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertOrReplace(final WeeklyTarget target,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfWeeklyTarget_1.insert(target);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final WeeklyTarget target, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfWeeklyTarget.handle(target);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementUsed(final long weekStart, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementUsed.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, weekStart);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementUsed.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<WeeklyTarget> observeForWeek(final long weekStart) {
    final String _sql = "SELECT * FROM weekly_target WHERE weekStartDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, weekStart);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"weekly_target"}, new Callable<WeeklyTarget>() {
      @Override
      @Nullable
      public WeeklyTarget call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final WeeklyTarget _result;
          if (_cursor.moveToFirst()) {
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _result = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
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
  public Object getForWeek(final long weekStart,
      final Continuation<? super WeeklyTarget> $completion) {
    final String _sql = "SELECT * FROM weekly_target WHERE weekStartDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, weekStart);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<WeeklyTarget>() {
      @Override
      @Nullable
      public WeeklyTarget call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final WeeklyTarget _result;
          if (_cursor.moveToFirst()) {
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _result = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
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

  @Override
  public Flow<WeeklyTarget> observeCurrent() {
    final String _sql = "SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"weekly_target"}, new Callable<WeeklyTarget>() {
      @Override
      @Nullable
      public WeeklyTarget call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final WeeklyTarget _result;
          if (_cursor.moveToFirst()) {
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _result = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
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
  public Flow<List<WeeklyTarget>> observeLastN(final int n) {
    final String _sql = "SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, n);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"weekly_target"}, new Callable<List<WeeklyTarget>>() {
      @Override
      @NonNull
      public List<WeeklyTarget> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final List<WeeklyTarget> _result = new ArrayList<WeeklyTarget>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WeeklyTarget _item;
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _item = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
            _result.add(_item);
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
  public Flow<List<WeeklyTarget>> observeRecent() {
    final String _sql = "SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT 10";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"weekly_target"}, new Callable<List<WeeklyTarget>>() {
      @Override
      @NonNull
      public List<WeeklyTarget> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final List<WeeklyTarget> _result = new ArrayList<WeeklyTarget>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WeeklyTarget _item;
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _item = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
            _result.add(_item);
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
  public Object getAll(final Continuation<? super List<WeeklyTarget>> $completion) {
    final String _sql = "SELECT * FROM weekly_target ORDER BY weekStartDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<WeeklyTarget>>() {
      @Override
      @NonNull
      public List<WeeklyTarget> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfWeekStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "weekStartDate");
          final int _cursorIndexOfAllowedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedSessions");
          final int _cursorIndexOfUsedSessions = CursorUtil.getColumnIndexOrThrow(_cursor, "usedSessions");
          final int _cursorIndexOfStallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "stallReason");
          final List<WeeklyTarget> _result = new ArrayList<WeeklyTarget>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WeeklyTarget _item;
            final long _tmpWeekStartDate;
            _tmpWeekStartDate = _cursor.getLong(_cursorIndexOfWeekStartDate);
            final int _tmpAllowedSessions;
            _tmpAllowedSessions = _cursor.getInt(_cursorIndexOfAllowedSessions);
            final int _tmpUsedSessions;
            _tmpUsedSessions = _cursor.getInt(_cursorIndexOfUsedSessions);
            final String _tmpStallReason;
            _tmpStallReason = _cursor.getString(_cursorIndexOfStallReason);
            _item = new WeeklyTarget(_tmpWeekStartDate,_tmpAllowedSessions,_tmpUsedSessions,_tmpStallReason);
            _result.add(_item);
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
