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
public final class EvalMetricsDao_Impl implements EvalMetricsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EvalMetrics> __insertionAdapterOfEvalMetrics;

  public EvalMetricsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEvalMetrics = new EntityInsertionAdapter<EvalMetrics>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `eval_metrics` (`id`,`phaseNumber`,`metricName`,`metricValue`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EvalMetrics entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getPhaseNumber());
        statement.bindString(3, entity.getMetricName());
        statement.bindString(4, entity.getMetricValue());
        statement.bindLong(5, entity.getTimestamp());
      }
    };
  }

  @Override
  public Object insert(final EvalMetrics metric, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEvalMetrics.insert(metric);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<EvalMetrics>> observeAll() {
    final String _sql = "SELECT * FROM eval_metrics ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"eval_metrics"}, new Callable<List<EvalMetrics>>() {
      @Override
      @NonNull
      public List<EvalMetrics> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhaseNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phaseNumber");
          final int _cursorIndexOfMetricName = CursorUtil.getColumnIndexOrThrow(_cursor, "metricName");
          final int _cursorIndexOfMetricValue = CursorUtil.getColumnIndexOrThrow(_cursor, "metricValue");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<EvalMetrics> _result = new ArrayList<EvalMetrics>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EvalMetrics _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpPhaseNumber;
            _tmpPhaseNumber = _cursor.getInt(_cursorIndexOfPhaseNumber);
            final String _tmpMetricName;
            _tmpMetricName = _cursor.getString(_cursorIndexOfMetricName);
            final String _tmpMetricValue;
            _tmpMetricValue = _cursor.getString(_cursorIndexOfMetricValue);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new EvalMetrics(_tmpId,_tmpPhaseNumber,_tmpMetricName,_tmpMetricValue,_tmpTimestamp);
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
  public Object getAllForPhase(final int phase,
      final Continuation<? super List<EvalMetrics>> $completion) {
    final String _sql = "SELECT * FROM eval_metrics WHERE phaseNumber = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, phase);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EvalMetrics>>() {
      @Override
      @NonNull
      public List<EvalMetrics> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhaseNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phaseNumber");
          final int _cursorIndexOfMetricName = CursorUtil.getColumnIndexOrThrow(_cursor, "metricName");
          final int _cursorIndexOfMetricValue = CursorUtil.getColumnIndexOrThrow(_cursor, "metricValue");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<EvalMetrics> _result = new ArrayList<EvalMetrics>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EvalMetrics _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpPhaseNumber;
            _tmpPhaseNumber = _cursor.getInt(_cursorIndexOfPhaseNumber);
            final String _tmpMetricName;
            _tmpMetricName = _cursor.getString(_cursorIndexOfMetricName);
            final String _tmpMetricValue;
            _tmpMetricValue = _cursor.getString(_cursorIndexOfMetricValue);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new EvalMetrics(_tmpId,_tmpPhaseNumber,_tmpMetricName,_tmpMetricValue,_tmpTimestamp);
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

  @Override
  public Object getAll(final Continuation<? super List<EvalMetrics>> $completion) {
    final String _sql = "SELECT * FROM eval_metrics ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EvalMetrics>>() {
      @Override
      @NonNull
      public List<EvalMetrics> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhaseNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phaseNumber");
          final int _cursorIndexOfMetricName = CursorUtil.getColumnIndexOrThrow(_cursor, "metricName");
          final int _cursorIndexOfMetricValue = CursorUtil.getColumnIndexOrThrow(_cursor, "metricValue");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<EvalMetrics> _result = new ArrayList<EvalMetrics>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EvalMetrics _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpPhaseNumber;
            _tmpPhaseNumber = _cursor.getInt(_cursorIndexOfPhaseNumber);
            final String _tmpMetricName;
            _tmpMetricName = _cursor.getString(_cursorIndexOfMetricName);
            final String _tmpMetricValue;
            _tmpMetricValue = _cursor.getString(_cursorIndexOfMetricValue);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new EvalMetrics(_tmpId,_tmpPhaseNumber,_tmpMetricName,_tmpMetricValue,_tmpTimestamp);
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
