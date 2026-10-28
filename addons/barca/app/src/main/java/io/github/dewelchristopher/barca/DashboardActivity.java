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
 * The full Barça dashboard on one 16:9 screen, in two columns that always fit: the next game and the form on the
 * left, the LaLiga table and the games after it on the right. Built once per data change from plain views and flat
 * shapes, so it costs the TV's GPU nothing per frame; only the countdown's text changes, every 30 seconds.
 */
public final class DashboardActivity extends Activity {
    private static final int GOLD = 0xFFEDBB00, GARNET = 0xFFA50044, BLUE = 0xFF004D98;
    private static final int TEXT = 0xFFFFFFFF, DIM = 0xB3FFFFFF, FAINT = 0x73FFFFFF;
    private static final int CARD = 0x1AFFFFFF, LINE = 0x14FFFFFF;
    private static final int WIN = 0xFF1E9E5A, DRAW = 0xFF6B7280, LOSS = 0xFFC81E3A;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter TINY = DateTimeFormatter.ofPattern("d/MM", Locale.FRENCH);

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
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF081634, 0xFF0F1030, 0xFF2A0820}));
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
        page.setPadding(px(44), px(26), px(44), px(18));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Header: one line, the club in gold, the page's name, and how fresh the data is.
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        View mark = new View(this);
        mark.setBackground(stripes());
        header.addView(mark, lp(px(6), px(30), 0, 0, px(14), 0));
        LinearLayout titles = vertical();
        titles.addView(text("FC BARCELONA", 11, GOLD, true, 0.3f));
        TextView title = text("Tableau de bord", 24, TEXT, true, -0.01f);
        title.setPadding(0, px(3), 0, 0);
        titles.addView(title);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        updated = text(freshness(), 11, FAINT, false, 0.04f);
        header.addView(updated);
        page.addView(header);

        if (!store.hasKey()) {
            page.addView(card(noKey()), lp(-1, -2, 0, px(20), 0, 0));
            return;
        }

        LinearLayout columns = horizontal();
        page.addView(columns, lp(-1, 0, 0, px(18), 0, 0));
        ((LinearLayout.LayoutParams) columns.getLayoutParams()).weight = 1;
        LinearLayout left = vertical();
        columns.addView(left, new LinearLayout.LayoutParams(0, -1, 1.1f));
        LinearLayout right = vertical();
        columns.addView(right, lp(0, -1, px(18), 0, 0, 0));
        ((LinearLayout.LayoutParams) right.getLayoutParams()).weight = 1f;

        long now = System.currentTimeMillis();
        List<Store.Match> next = store.upcoming(now);
        // Left: the next game takes the room the form leaves; right: the table does, over the games after.
        left.addView(card(nextGame(next.isEmpty() ? null : next.get(0))), new LinearLayout.LayoutParams(-1, 0, 1));
        left.addView(card(form(store.lastResults(5))), lp(-1, -2, 0, px(14), 0, 0));
        right.addView(card(table(store.table())), new LinearLayout.LayoutParams(-1, 0, 1));
        if (next.size() > 1) right.addView(card(later(next.subList(1, Math.min(3, next.size())))), lp(-1, -2, 0, px(14), 0, 0));

        TextView source = text("Données football-data.org", 9, FAINT, false, 0.05f);
        source.setGravity(Gravity.END);
        page.addView(source, lp(-1, -2, 0, px(8), 0, 0));
    }

    private View nextGame(Store.Match m) {
        LinearLayout box = vertical();
        box.addView(kicker("PROCHAIN MATCH"));
        shownNext = m;
        if (m == null) {
            countdown = null;
            box.addView(text("Aucun match prévu pour l'instant.", 16, DIM, false, 0));
            return box;
        }
        String meta = m.competition + (m.matchday > 0 && "LaLiga".equals(m.competition) ? " · Journée " + m.matchday : "");
        box.addView(text(meta, 12, DIM, false, 0.02f));

        // The two sides with their badges, centred in the card's free height.
        LinearLayout teams = horizontal();
        teams.setGravity(Gravity.CENTER);
        String homeName = m.home ? "Barça" : m.opponent, awayName = m.home ? m.opponent : "Barça";
        teams.addView(side(homeName, m.home), new LinearLayout.LayoutParams(0, -2, 1));
        TextView vs = text("VS", 13, GOLD, true, 0.25f);
        vs.setGravity(Gravity.CENTER);
        teams.addView(vs, new LinearLayout.LayoutParams(px(52), -2));
        teams.addView(side(awayName, !m.home), new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(teams, new LinearLayout.LayoutParams(-1, 0, 1));
        ((LinearLayout.LayoutParams) teams.getLayoutParams()).gravity = Gravity.CENTER;

        box.addView(divider(), lp(-1, 1, 0, 0, 0, px(10)));
        LinearLayout foot = horizontal();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        ZoneId zone = ZoneId.systemDefault();
        Instant start = Instant.ofEpochMilli(m.startMs);
        LinearLayout when = vertical();
        when.addView(text(capitalise(DAY.format(start.atZone(zone))), 14, TEXT, true, 0));
        TextView where = text(TIME.format(start.atZone(zone)) + (m.home ? " · à domicile" : " · à l'extérieur"), 12, DIM, false, 0);
        where.setPadding(0, px(3), 0, 0);
        when.addView(where);
        foot.addView(when, new LinearLayout.LayoutParams(0, -2, 1));
        countdown = text("", 14, TEXT, true, 0);
        countdown.setPadding(px(12), px(6), px(12), px(6));
        foot.addView(countdown);
        box.addView(foot);
        updateCountdown();
        return box;
    }

    /** A badge with the club's first letters over its name: Barça's in blaugrana, the opponent's in grey. */
    private View side(String name, boolean barca) {
        LinearLayout s = vertical();
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView badge = text(barca ? "FCB" : initials(name), 15, barca ? GOLD : TEXT, true, 0.04f);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = barca
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {BLUE, GARNET})
                : new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (!barca) circle.setColor(0x33FFFFFF);
        badge.setBackground(circle);
        s.addView(badge, new LinearLayout.LayoutParams(px(58), px(58)));
        TextView label = text(name, 18, barca ? GOLD : TEXT, true, -0.01f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        s.addView(label, lp(-1, -2, 0, px(8), 0, 0));
        return s;
    }

    private void updateCountdown() {
        if (updated != null) updated.setText(freshness());
        if (countdown == null || shownNext == null) return;
        long left = shownNext.startMs - System.currentTimeMillis();
        if (shownNext.live() || (left <= 0 && left > -Store.GAME_MS)) {
            countdown.setText("● En direct");
            countdown.setBackground(rounded(0x33FF4D5E, px(14)));
            countdown.setTextColor(0xFFFF6B79);
        } else if (left > 0) {
            countdown.setText("Dans " + duration(left));
            countdown.setBackground(rounded(0x26EDBB00, px(14)));
            countdown.setTextColor(GOLD);
        } else {
            countdown.setText("Terminé");
            countdown.setBackground(rounded(CARD, px(14)));
            countdown.setTextColor(DIM);
        }
    }

    private View later(List<Store.Match> games) {
        LinearLayout box = vertical();
        box.addView(kicker("ENSUITE"));
        ZoneId zone = ZoneId.systemDefault();
        for (Store.Match m : games) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(5), 0, 0);
            row.addView(text(capitalise(SHORT.format(Instant.ofEpochMilli(m.startMs).atZone(zone))), 12, DIM, false, 0),
                    new LinearLayout.LayoutParams(px(150), -2));
            TextView fixture = text(m.fixture(), 13, TEXT, true, 0);
            fixture.setSingleLine(true);
            fixture.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(fixture, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(m.competition, 11, FAINT, false, 0));
            box.addView(row);
        }
        return box;
    }

    /** Five equal cells, oldest to newest: the result letter on its colour, the score and the date under it. */
    private View form(List<Store.Match> results) {
        LinearLayout box = vertical();
        box.addView(kicker("FORME DU MOMENT"));
        if (results.isEmpty()) {
            box.addView(text("Pas encore de résultat.", 13, DIM, false, 0));
            return box;
        }
        LinearLayout cells = horizontal();
        cells.setPadding(0, px(6), 0, 0);
        ZoneId zone = ZoneId.systemDefault();
        for (int i = 0; i < results.size(); i++) {
            Store.Match m = results.get(i);
            char r = m.result();
            LinearLayout cell = vertical();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView letter = text(String.valueOf(r), 14, TEXT, true, 0);
            letter.setGravity(Gravity.CENTER);
            letter.setBackground(rounded(r == 'V' ? WIN : r == 'N' ? DRAW : LOSS, px(6)));
            cell.addView(letter, new LinearLayout.LayoutParams(-1, px(28)));
            TextView score = text(m.goalsFor + "-" + m.goalsAgainst, 13, TEXT, true, 0);
            score.setGravity(Gravity.CENTER);
            cell.addView(score, lp(-1, -2, 0, px(6), 0, 0));
            TextView who = text((m.home ? "" : "@ ") + initials(m.opponent) + " · " + TINY.format(Instant.ofEpochMilli(m.startMs).atZone(zone)),
                    10, FAINT, false, 0);
            who.setGravity(Gravity.CENTER);
            who.setSingleLine(true);
            cell.addView(who, lp(-1, -2, 0, px(3), 0, 0));
            cells.addView(cell, lp(0, -2, i == 0 ? 0 : px(8), 0, 0, 0));
            ((LinearLayout.LayoutParams) cell.getLayoutParams()).weight = 1;
        }
        box.addView(cells);
        return box;
    }

    /** The first five, then Barça's neighbours if it is lower down, so its row is always on the card. */
    private View table(List<Store.Row> rows) {
        LinearLayout box = vertical();
        box.addView(kicker("LALIGA · CLASSEMENT"));
        if (rows.isEmpty()) {
            box.addView(text("Classement pas encore chargé.", 13, DIM, false, 0));
            return box;
        }
        box.addView(tableRow("#", "Équipe", "J", "Diff", "Pts", FAINT, false, 0, false), lp(-1, -2, 0, px(4), 0, px(2)));
        int barca = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).teamId == Store.BARCA) barca = i;
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(5, rows.size()); i++) shown.add(i);
        if (barca >= 5) {
            for (int i = Math.max(5, barca - 1); i <= Math.min(rows.size() - 1, barca + 1); i++) shown.add(i);
        }
        int previous = -1, n = 0;
        for (int i : shown) {
            if (previous >= 0 && i != previous + 1) box.addView(tableRow("", "···", "", "", "", FAINT, false, 0, false));
            Store.Row r = rows.get(i);
            boolean us = r.teamId == Store.BARCA;
            String diff = (r.goalDifference > 0 ? "+" : "") + r.goalDifference;
            box.addView(tableRow(String.valueOf(r.position), r.team, String.valueOf(r.played), diff,
                    String.valueOf(r.points), us ? GOLD : TEXT, us, r.position <= 4 ? BLUE : 0, n++ % 2 == 1));
            previous = i;
        }
        Store.Row us = store.barcaRow();
        if (us != null) {
            TextView summary = text(us.won + " victoires · " + us.draw + " nuls · " + us.lost + " défaites", 11, DIM, false, 0);
            summary.setPadding(px(8), px(8), 0, 0);
            box.addView(summary);
        }
        return box;
    }

    private View tableRow(String pos, String team, String played, String diff, String pts, int colour, boolean bold,
                          int marker, boolean zebra) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(5), px(8), px(5));
        if (bold) row.setBackground(rounded(0x2EEDBB00, px(6)));
        else if (zebra) row.setBackground(rounded(LINE, px(6)));
        View mark = new View(this);
        if (marker != 0) mark.setBackground(rounded(marker | 0xFF000000, px(1)));
        row.addView(mark, lp(px(3), px(14), 0, 0, px(8), 0));
        row.addView(cell(pos, colour, bold, px(24), Gravity.START));
        row.addView(cell(team, colour, bold, 0, Gravity.START), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(cell(played, colour, bold, px(30), Gravity.END));
        row.addView(cell(diff, colour, bold, px(42), Gravity.END));
        row.addView(cell(pts, colour, true, px(40), Gravity.END));
        return row;
    }

    private TextView cell(String s, int colour, boolean bold, int width, int gravity) {
        TextView t = text(s, 13, colour, bold, 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(gravity);
        if (width > 0) t.setLayoutParams(new LinearLayout.LayoutParams(width, -2));
        return t;
    }

    private View noKey() {
        LinearLayout box = vertical();
        box.addView(kicker("À CONFIGURER"));
        box.addView(text("Il manque la clé football-data.org.", 20, TEXT, true, 0));
        TextView how = text("Crée un compte gratuit sur football-data.org, puis depuis ton PC :\n\n"
                + "adb shell am broadcast -n io.github.dewelchristopher.barca/.KeyReceiver "
                + "-a io.github.dewelchristopher.barca.SET_KEY --es key TA_CLE", 13, DIM, false, 0);
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
        return error == null ? "Mis à jour " + age : "Hors ligne · " + error;
    }

    private static String duration(long ms) {
        long minutes = Math.max(1, (ms + 59_999) / 60_000);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h " + (minutes % 60) + " min";
        return (hours / 24) + " j " + (hours % 24) + " h";
    }

    /** "Real Madrid" as "RM", "Girona" as "GIR". */
    static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(3, words.length); i++) if (!words[i].isEmpty()) b.append(words[i].charAt(0));
            return b.toString().toUpperCase(Locale.FRENCH);
        }
        return name.substring(0, Math.min(3, name.length())).toUpperCase(Locale.FRENCH);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- small view helpers ------------------------------------------------------------------

    private View card(View content) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(CARD, px(14)));
        card.setPadding(px(18), px(14), px(18), px(14));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    private GradientDrawable stripes() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BLUE, GARNET});
        g.setCornerRadius(px(3));
        return g;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private TextView kicker(String s) {
        TextView t = text(s, 10, GOLD, true, 0.22f);
        t.setPadding(0, 0, 0, px(6));
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
