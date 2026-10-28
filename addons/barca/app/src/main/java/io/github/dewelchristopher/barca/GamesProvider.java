package io.github.dewelchristopher.barca;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.List;
import java.util.Locale;

/**
 * Barça's next games for the launcher's game_day panel, in Game Day's columns (docs/modules.md): one turn per game,
 * up to three, with Barça's LaLiga place on each line. A query answers from the cache at once; a stale cache is
 * refreshed in the background and Home is told when the new copy is in.
 */
public final class GamesProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.dewelchristopher.barca.games";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    private static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};
    private static final int GARNET = 0xFFA50044, BLUE = 0xFF1B6FD1;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context ctx = getContext();
        MatrixCursor c = new MatrixCursor(COLUMNS);
        if (ctx == null) return c;
        Store store = new Store(ctx);
        long now = System.currentTimeMillis();
        if (store.stale(now)) refreshInBackground(ctx);
        Store.Row row = store.barcaRow();
        String place = row == null ? "" : String.format(Locale.FRANCE, " · %s, %d pts", ordinal(row.position), row.points);
        List<Store.Match> next = store.upcoming(now);
        for (int i = 0; i < Math.min(3, next.size()); i++) {
            Store.Match m = next.get(i);
            String line = m.fixture() + " · " + m.competition + ("LaLiga".equals(m.competition) ? place : "");
            c.addRow(new Object[] {line, "Coup d'envoi", m.startMs, m.startMs + Store.GAME_MS, i % 2 == 0 ? GARNET : BLUE,
                    "barca", 20});
        }
        return c;
    }

    static void refreshInBackground(Context ctx) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            if (new Store(app).refresh()) app.getContentResolver().notifyChange(TURNS, null);
        }, "barca-refresh").start();
    }

    static String ordinal(int position) {
        return position == 1 ? "1er" : position + "e";
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
