package io.github.dewelchristopher.barca;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The full Barça dashboard: the next game with its countdown, the games after it, the last five results and the
 * LaLiga table around Barça. Built once per data change from plain views and flat shapes, so it costs the TV's GPU
 * nothing per frame; only the countdown's text changes, every 30 seconds.
 */
public final class DashboardActivity extends Activity {
    private static final int GOLD = 0xFFEDBB00, GARNET = 0xFFA50044, BLUE = 0xFF004D98;
    private static final int TEXT = 0xFFFFFFFF, DIM = 0xB3FFFFFF, FAINT = 0x80FFFFFF;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRENCH);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private float dp;
    private FrameLayout root;
    private TextView countdown;
    private TextView updated;
    private Store.Match shownNext;
    private boolean resumed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        store = new Store(this);
        root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF0A1A44, 0xFF14123A, 0xFF3A0A26});
        root.setBackground(bg);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        build();
        handler.post(tick);
        if (store.hasKey() && System.currentTimeMillis() - store.fetchedAt() > 15 * 60_000L) {
            new Thread(() -> {
                if (store.refresh()) {
                    getContentResolver().notifyChange(GamesProvider.TURNS, null);
                    handler.post(() -> { if (resumed) build(); });
                }
            }, "barca-dashboard").start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(tick);
    }

    private void build() {
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setPadding(px(48), px(32), px(48), px(24));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Header: the club in gold, the page's name, and how fresh the data is.
        LinearLayout header = horizontal();
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titles = vertical();
        titles.addView(text("FC BARCELONA", 13, GOLD, true, 0.3f));
        TextView title = text("Tableau de bord", 34, TEXT, false, -0.01f);
        title.setPadding(0, px(4), 0, 0);
        titles.addView(title);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        updated = text(freshness(), 12, FAINT, false, 0.05f);
        updated.setGravity(Gravity.END);
        header.addView(updated);
        page.addView(header);
        page.addView(stripe(), lp(-1, px(3), 0, px(14), 0, px(18)));

        if (!store.hasKey()) {
            page.addView(card(noKey()), lp(-1, -2, 0, 0, 0, 0));
            return;
        }

        LinearLayout columns = horizontal();
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout left = vertical();
        columns.addView(left, new LinearLayout.LayoutParams(0, -1, 1.15f));
        LinearLayout right = vertical();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
        rp.leftMargin = px(24);
        columns.addView(right, rp);

        long now = System.currentTimeMillis();
        List<Store.Match> next = store.upcoming(now);
        left.addView(card(nextGame(next.isEmpty() ? null : next.get(0))), new LinearLayout.LayoutParams(-1, -2));
        if (next.size() > 1) left.addView(card(later(next.subList(1, Math.min(3, next.size())))), lp(-1, -2, 0, px(16), 0, 0));
        left.addView(card(form(store.lastResults(5))), lp(-1, -2, 0, px(16), 0, 0));
        right.addView(card(table(store.table())), new LinearLayout.LayoutParams(-1, -2));

        TextView source = text("Données : football-data.org", 10, FAINT, false, 0.05f);
        source.setGravity(Gravity.END);
        page.addView(source, lp(-1, -2, 0, px(10), 0, 0));
    }

    private View nextGame(Store.Match m) {
        LinearLayout box = vertical();
        box.addView(kicker(m == null ? "PROCHAIN MATCH" : "PROCHAIN MATCH · " + m.competition.toUpperCase(Locale.FRENCH)
                + (m.matchday > 0 && "LaLiga".equals(m.competition) ? " · J" + m.matchday : "")));
        shownNext = m;
        if (m == null) {
            box.addView(text("Aucun match prévu pour l'instant.", 18, DIM, false, 0));
            countdown = null;
            return box;
        }
        LinearLayout teams = horizontal();
        teams.setGravity(Gravity.CENTER_VERTICAL);
        teams.setPadding(0, px(10), 0, px(6));
        String homeName = m.home ? "Barça" : m.opponent, awayName = m.home ? m.opponent : "Barça";
        teams.addView(team(homeName, m.home), new LinearLayout.LayoutParams(0, -2, 1));
        TextView vs = text("VS", 16, GOLD, true, 0.2f);
        vs.setPadding(px(18), 0, px(18), 0);
        teams.addView(vs);
        TextView away = team(awayName, !m.home);
        away.setGravity(Gravity.END);
        teams.addView(away, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(teams);
        ZoneId zone = ZoneId.systemDefault();
        Instant start = Instant.ofEpochMilli(m.startMs);
        String when = capitalise(DAY.format(start.atZone(zone))) + " · " + TIME.format(start.atZone(zone))
                + (m.home ? " · à domicile" : " · à l'extérieur");
        box.addView(text(when, 15, DIM, false, 0));
        countdown = text("", 22, TEXT, true, 0);
        countdown.setPadding(0, px(10), 0, 0);
        box.addView(countdown);
        updateCountdown();
        return box;
    }

    private TextView team(String name, boolean barca) {
        TextView t = text(name, 30, barca ? GOLD : TEXT, true, -0.01f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private void updateCountdown() {
        if (countdown == null || shownNext == null) return;
        long now = System.currentTimeMillis();
        long left = shownNext.startMs - now;
        if (shownNext.live() || (left <= 0 && left > -Store.GAME_MS)) {
            countdown.setText("● En direct");
            countdown.setTextColor(0xFFFF4D5E);
        } else if (left > 0) {
            countdown.setText("Coup d'envoi dans " + duration(left));
            countdown.setTextColor(TEXT);
        } else {
            countdown.setText("Terminé, résultat à venir");
            countdown.setTextColor(DIM);
        }
        if (updated != null) updated.setText(freshness());
    }

    private View later(List<Store.Match> games) {
        LinearLayout box = vertical();
        box.addView(kicker("ENSUITE"));
        ZoneId zone = ZoneId.systemDefault();
        for (Store.Match m : games) {
            LinearLayout row = horizontal();
            row.setPadding(0, px(6), 0, 0);
            TextView date = text(capitalise(SHORT.format(Instant.ofEpochMilli(m.startMs).atZone(zone))), 13, DIM, false, 0);
            row.addView(date, new LinearLayout.LayoutParams(px(170), -2));
            TextView fixture = text(m.fixture(), 15, TEXT, false, 0);
            fixture.setSingleLine(true);
            fixture.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(fixture, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(m.competition, 12, FAINT, false, 0));
            box.addView(row);
        }
        return box;
    }

    private View form(List<Store.Match> results) {
        LinearLayout box = vertical();
        box.addView(kicker("FORME · 5 DERNIERS MATCHS"));
        if (results.isEmpty()) {
            box.addView(text("Pas encore de résultat.", 14, DIM, false, 0));
            return box;
        }
        LinearLayout chips = horizontal();
        chips.setPadding(0, px(8), 0, px(8));
        for (Store.Match m : results) {
            char r = m.result();
            TextView chip = text(String.valueOf(r), 16, TEXT, true, 0);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(r == 'V' ? 0xFF1E9E5A : r == 'N' ? 0xFF6B7280 : 0xFFC81E3A, px(6)));
            chips.addView(chip, lp(px(36), px(36), 0, 0, px(8), 0));
        }
        box.addView(chips);
        Store.Match last = results.get(results.size() - 1);
        String score = last.home ? "Barça " + last.goalsFor + "-" + last.goalsAgainst + " " + last.opponent
                : last.opponent + " " + last.goalsAgainst + "-" + last.goalsFor + " Barça";
        box.addView(text("Dernier : " + score + " · " + last.competition, 13, DIM, false, 0));
        return box;
    }

    /** The first five, then Barça's neighbours if it is lower down, so its row is always on the card. */
    private View table(List<Store.Row> rows) {
        LinearLayout box = vertical();
        box.addView(kicker("LALIGA · CLASSEMENT"));
        if (rows.isEmpty()) {
            box.addView(text("Classement pas encore chargé.", 14, DIM, false, 0));
            return box;
        }
        box.addView(tableRow("#", "Équipe", "J", "Diff", "Pts", FAINT, false, 0));
        int barca = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).teamId == Store.BARCA) barca = i;
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(5, rows.size()); i++) shown.add(i);
        if (barca >= 5) {
            for (int i = Math.max(5, barca - 1); i <= Math.min(rows.size() - 1, barca + 1); i++) shown.add(i);
        }
        int previous = -1;
        for (int i : shown) {
            if (previous >= 0 && i != previous + 1) box.addView(tableRow("…", "", "", "", "", FAINT, false, 0));
            Store.Row r = rows.get(i);
            boolean us = r.teamId == Store.BARCA;
            String diff = (r.goalDifference > 0 ? "+" : "") + r.goalDifference;
            box.addView(tableRow(String.valueOf(r.position), r.team, String.valueOf(r.played), diff,
                    String.valueOf(r.points), us ? GOLD : TEXT, us, r.position <= 4 ? BLUE : 0));
            previous = i;
        }
        Store.Row us = store.barcaRow();
        if (us != null) {
            TextView summary = text(us.won + " V · " + us.draw + " N · " + us.lost + " D", 13, DIM, false, 0);
            summary.setPadding(0, px(10), 0, 0);
            box.addView(summary);
        }
        return box;
    }

    private View tableRow(String pos, String team, String played, String diff, String pts, int colour, boolean bold,
                          int marker) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(5), px(8), px(5));
        if (bold) row.setBackground(rounded(0x33EDBB00, px(6)));
        View mark = new View(this);
        if (marker != 0) mark.setBackgroundColor(marker);
        row.addView(mark, lp(px(3), px(16), 0, 0, px(8), 0));
        row.addView(cell(pos, colour, bold, px(26), Gravity.START));
        TextView name = cell(team, colour, bold, 0, Gravity.START);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(cell(played, colour, bold, px(34), Gravity.END));
        row.addView(cell(diff, colour, bold, px(46), Gravity.END));
        row.addView(cell(pts, colour, true, px(44), Gravity.END));
        return row;
    }

    private TextView cell(String s, int colour, boolean bold, int width, int gravity) {
        TextView t = text(s, 15, colour, bold, 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(gravity);
        if (width > 0) t.setLayoutParams(new LinearLayout.LayoutParams(width, -2));
        return t;
    }

    private View noKey() {
        LinearLayout box = vertical();
        box.addView(kicker("À CONFIGURER"));
        box.addView(text("Il manque la clé football-data.org.", 22, TEXT, true, 0));
        TextView how = text("Crée un compte gratuit sur football-data.org, puis depuis ton PC :\n\n"
                + "adb shell am broadcast -n io.github.dewelchristopher.barca/.KeyReceiver "
                + "-a io.github.dewelchristopher.barca.SET_KEY --es key TA_CLE", 15, DIM, false, 0);
        how.setPadding(0, px(12), 0, 0);
        box.addView(how);
        return box;
    }

    private String freshness() {
        long at = store.fetchedAt();
        String error = store.error();
        if (at == 0) return error == null ? "" : "Erreur : " + error;
        long min = Math.max(0, (System.currentTimeMillis() - at) / 60_000L);
        String age = min < 1 ? "à l'instant" : min < 60 ? "il y a " + min + " min" : "il y a " + (min / 60) + " h";
        return (error == null ? "Mis à jour " + age : "Hors ligne (" + error + ")");
    }

    private static String duration(long ms) {
        long minutes = Math.max(1, (ms + 59_999) / 60_000);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h " + (minutes % 60) + " min";
        return (hours / 24) + " j " + (hours % 24) + " h";
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- small view helpers ------------------------------------------------------------------

    private View card(View content) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(0x1FFFFFFF, px(14)));
        card.setPadding(px(20), px(16), px(20), px(16));
        card.addView(content);
        return card;
    }

    private View stripe() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {BLUE, GARNET, GOLD});
        View v = new View(this);
        v.setBackground(g);
        return v;
    }

    private TextView kicker(String s) {
        TextView t = text(s, 11, GOLD, true, 0.22f);
        t.setPadding(0, 0, 0, px(4));
        return t;
    }

    private TextView text(String s, float sp, int colour, boolean bold, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        if (spacing != 0) t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable rounded(int colour, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private int px(float value) {
        return Math.round(value * dp);
    }
}
