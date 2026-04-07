package com.impulsive.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class TriggerLogDao_Impl implements TriggerLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TriggerLog> __insertionAdapterOfTriggerLog;

  public TriggerLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTriggerLog = new EntityInsertionAdapter<TriggerLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `trigger_log` (`id`,`timestamp`,`triggerType`,`outcome`,`holdDurationSeconds`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TriggerLog entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getTriggerType());
        statement.bindString(4, entity.getOutcome());
        statement.bindDouble(5, entity.getHoldDurationSeconds());
      }
    };
  }

  @Override
  public Object insert(final TriggerLog log, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTriggerLog.insert(log);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TriggerLog>> observeAll() {
    final String _sql = "SELECT * FROM trigger_log ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"trigger_log"}, new Callable<List<TriggerLog>>() {
      @Override
      @NonNull
      public List<TriggerLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfTriggerType = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerType");
          final int _cursorIndexOfOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "outcome");
          final int _cursorIndexOfHoldDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "holdDurationSeconds");
          final List<TriggerLog> _result = new ArrayList<TriggerLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TriggerLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpTriggerType;
            _tmpTriggerType = _cursor.getString(_cursorIndexOfTriggerType);
            final String _tmpOutcome;
            _tmpOutcome = _cursor.getString(_cursorIndexOfOutcome);
            final float _tmpHoldDurationSeconds;
            _tmpHoldDurationSeconds = _cursor.getFloat(_cursorIndexOfHoldDurationSeconds);
            _item = new TriggerLog(_tmpId,_tmpTimestamp,_tmpTriggerType,_tmpOutcome,_tmpHoldDurationSeconds);
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
  public Flow<List<TriggerLog>> observeSince(final long weekStart) {
    final String _sql = "SELECT * FROM trigger_log WHERE timestamp >= ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, weekStart);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"trigger_log"}, new Callable<List<TriggerLog>>() {
      @Override
      @NonNull
      public List<TriggerLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfTriggerType = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerType");
          final int _cursorIndexOfOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "outcome");
          final int _cursorIndexOfHoldDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "holdDurationSeconds");
          final List<TriggerLog> _result = new ArrayList<TriggerLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TriggerLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpTriggerType;
            _tmpTriggerType = _cursor.getString(_cursorIndexOfTriggerType);
            final String _tmpOutcome;
            _tmpOutcome = _cursor.getString(_cursorIndexOfOutcome);
            final float _tmpHoldDurationSeconds;
            _tmpHoldDurationSeconds = _cursor.getFloat(_cursorIndexOfHoldDurationSeconds);
            _item = new TriggerLog(_tmpId,_tmpTimestamp,_tmpTriggerType,_tmpOutcome,_tmpHoldDurationSeconds);
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
  public Flow<Integer> countSince(final long weekStart) {
    final String _sql = "SELECT COUNT(*) FROM trigger_log WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, weekStart);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"trigger_log"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Flow<List<TriggerLog>> observeForDateRange(final long startMs, final long endMs) {
    final String _sql = "SELECT * FROM trigger_log WHERE timestamp >= ? AND timestamp < ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startMs);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endMs);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"trigger_log"}, new Callable<List<TriggerLog>>() {
      @Override
      @NonNull
      public List<TriggerLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfTriggerType = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerType");
          final int _cursorIndexOfOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "outcome");
          final int _cursorIndexOfHoldDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "holdDurationSeconds");
          final List<TriggerLog> _result = new ArrayList<TriggerLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TriggerLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpTriggerType;
            _tmpTriggerType = _cursor.getString(_cursorIndexOfTriggerType);
            final String _tmpOutcome;
            _tmpOutcome = _cursor.getString(_cursorIndexOfOutcome);
            final float _tmpHoldDurationSeconds;
            _tmpHoldDurationSeconds = _cursor.getFloat(_cursorIndexOfHoldDurationSeconds);
            _item = new TriggerLog(_tmpId,_tmpTimestamp,_tmpTriggerType,_tmpOutcome,_tmpHoldDurationSeconds);
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
  public Object getAll(final Continuation<? super List<TriggerLog>> $completion) {
    final String _sql = "SELECT * FROM trigger_log ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TriggerLog>>() {
      @Override
      @NonNull
      public List<TriggerLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfTriggerType = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerType");
          final int _cursorIndexOfOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "outcome");
          final int _cursorIndexOfHoldDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "holdDurationSeconds");
          final List<TriggerLog> _result = new ArrayList<TriggerLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TriggerLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpTriggerType;
            _tmpTriggerType = _cursor.getString(_cursorIndexOfTriggerType);
            final String _tmpOutcome;
            _tmpOutcome = _cursor.getString(_cursorIndexOfOutcome);
            final float _tmpHoldDurationSeconds;
            _tmpHoldDurationSeconds = _cursor.getFloat(_cursorIndexOfHoldDurationSeconds);
            _item = new TriggerLog(_tmpId,_tmpTimestamp,_tmpTriggerType,_tmpOutcome,_tmpHoldDurationSeconds);
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
