package io.github.dewelchristopher.barca;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FC Barcelona's season and the LaLiga table from football-data.org (v4), cached on the TV. Two requests per refresh,
 * at most every {@link #FRESH_MS}, well inside the free tier's ten a minute. A failed refresh keeps the last good copy.
 */
final class Store {
    static final int BARCA = 81;
    private static final String API = "https://api.football-data.org/v4";
    /** How long a copy is good for; shorter around a game so the result and the next fixture arrive soon after. */
    private static final long FRESH_MS = 3 * 60 * 60_000L;
    private static final long FRESH_NEAR_GAME_MS = 20 * 60_000L;
    static final long GAME_MS = 115 * 60_000L;
    private static final AtomicBoolean fetching = new AtomicBoolean();

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("barca", Context.MODE_PRIVATE);
    }

    boolean hasKey() {
        return !key().isEmpty();
    }

    private String key() {
        return prefs.getString("key", "").trim();
    }

    void setKey(String key) {
        prefs.edit().putString("key", key == null ? "" : key.trim()).putLong("fetched", 0).remove("error").apply();
    }

    long fetchedAt() {
        return prefs.getLong("fetched", 0);
    }

    /** The last refresh's error, or null when it worked. */
    String error() {
        return prefs.getString("error", null);
    }

    boolean stale(long now) {
        if (!hasKey()) return false;
        long age = now - fetchedAt();
        if (age > FRESH_MS) return true;
        // A game that has started (or should be over) in the cached copy: look again sooner.
        for (Match m : matches()) {
            if (!m.finished() && m.startMs <= now) return age > FRESH_NEAR_GAME_MS;
        }
        return false;
    }

    /** Fetches both documents; true when the copy changed. Only one refresh runs at a time. */
    boolean refresh() {
        if (!hasKey() || !fetching.compareAndSet(false, true)) return false;
        try {
            String matches = get("/teams/" + BARCA + "/matches");
            String table = get("/competitions/PD/standings");
            new JSONObject(matches);   // both must parse before they replace the good copy
            new JSONObject(table);
            prefs.edit().putString("matches", matches).putString("table", table)
                    .putLong("fetched", System.currentTimeMillis()).remove("error").apply();
            return true;
        } catch (IOException | JSONException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // Keep the old copy, but do not hammer the service: wait as if it had worked for a while.
            prefs.edit().putString("error", why)
                    .putLong("fetched", Math.max(fetchedAt(), System.currentTimeMillis() - FRESH_MS + 15 * 60_000L)).apply();
            return true;
        } finally {
            fetching.set(false);
        }
    }

    private String get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        try {
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("X-Auth-Token", key());
            int code = c.getResponseCode();
            if (code == 400 || code == 403) throw new IOException("clé refusée (HTTP " + code + ")");
            if (code == 429) throw new IOException("trop de requêtes, nouvel essai plus tard");
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /** Every match of the season in the cache, in date order. */
    List<Match> matches() {
        List<Match> out = new ArrayList<>();
        try {
            JSONArray list = new JSONObject(prefs.getString("matches", "{}")).optJSONArray("matches");
            for (int i = 0; list != null && i < list.length(); i++) {
                Match m = Match.from(list.optJSONObject(i));
                if (m != null) out.add(m);
            }
        } catch (JSONException ignored) {
            // A copy that does not parse is treated as none.
        }
        Collections.sort(out, Comparator.comparingLong(m -> m.startMs));
        return out;
    }

    /** Games not yet over, soonest first (a game on now first of all). */
    List<Match> upcoming(long now) {
        List<Match> out = new ArrayList<>();
        for (Match m : matches()) {
            if (m.cancelled() || m.finished()) continue;
            if (m.startMs + GAME_MS > now) out.add(m);
        }
        return out;
    }

    /** The last finished games, oldest first. */
    List<Match> lastResults(int count) {
        List<Match> done = new ArrayList<>();
        for (Match m : matches()) if (m.finished()) done.add(m);
        return done.subList(Math.max(0, done.size() - count), done.size());
    }

    /** The LaLiga table, first place first. */
    List<Row> table() {
        List<Row> out = new ArrayList<>();
        try {
            JSONArray standings = new JSONObject(prefs.getString("table", "{}")).optJSONArray("standings");
            JSONArray rows = null;
            for (int i = 0; standings != null && i < standings.length(); i++) {
                JSONObject s = standings.optJSONObject(i);
                if (s != null && "TOTAL".equals(s.optString("type"))) rows = s.optJSONArray("table");
            }
            for (int i = 0; rows != null && i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i);
                JSONObject team = r == null ? null : r.optJSONObject("team");
                if (team == null) continue;
                out.add(new Row(r.optInt("position"), team.optInt("id"), name(team), r.optInt("playedGames"),
                        r.optInt("won"), r.optInt("draw"), r.optInt("lost"), r.optInt("goalDifference"), r.optInt("points")));
            }
        } catch (JSONException ignored) {
            // As for matches.
        }
        return out;
    }

    /** Barça's row in the table, or null before the first fetch. */
    Row barcaRow() {
        for (Row r : table()) if (r.teamId == BARCA) return r;
        return null;
    }

    static String name(JSONObject team) {
        String n = team.optString("shortName", "");
        if (n.isEmpty() || "null".equals(n)) n = team.optString("name", "?");
        return n;
    }

    /** French names for the competitions Barça plays in; any other keeps the service's name. */
    static String competition(String code, String name) {
        switch (code) {
            case "PD": return "LaLiga";
            case "CL": return "Ligue des champions";
            case "CDR": return "Coupe du Roi";
            case "SC": case "SCE": return "Supercoupe d'Espagne";
            default: return name;
        }
    }

    static final class Match {
        final long startMs;
        final String status;
        final String competition;
        final int matchday;
        final boolean home;
        final String opponent;
        final int goalsFor, goalsAgainst;
        final String winner;

        private Match(long startMs, String status, String competition, int matchday, boolean home, String opponent,
                      int goalsFor, int goalsAgainst, String winner) {
            this.startMs = startMs;
            this.status = status;
            this.competition = competition;
            this.matchday = matchday;
            this.home = home;
            this.opponent = opponent;
            this.goalsFor = goalsFor;
            this.goalsAgainst = goalsAgainst;
            this.winner = winner;
        }

        static Match from(JSONObject o) {
            if (o == null) return null;
            try {
                long start = Instant.parse(o.getString("utcDate")).toEpochMilli();
                JSONObject h = o.getJSONObject("homeTeam"), a = o.getJSONObject("awayTeam");
                boolean home = h.optInt("id") == BARCA;
                JSONObject comp = o.optJSONObject("competition");
                String compName = comp == null ? "" : competition(comp.optString("code"), comp.optString("name"));
                JSONObject full = o.optJSONObject("score") == null ? null : o.optJSONObject("score").optJSONObject("fullTime");
                int hg = full == null || full.isNull("home") ? -1 : full.optInt("home");
                int ag = full == null || full.isNull("away") ? -1 : full.optInt("away");
                String w = o.optJSONObject("score") == null ? "" : o.optJSONObject("score").optString("winner", "");
                return new Match(start, o.optString("status"), compName, o.optInt("matchday"), home,
                        name(home ? a : h), home ? hg : ag, home ? ag : hg, w);
            } catch (JSONException | RuntimeException e) {
                return null;
            }
        }

        boolean finished() {
            return "FINISHED".equals(status) || "AWARDED".equals(status);
        }

        boolean cancelled() {
            return "CANCELLED".equals(status) || "POSTPONED".equals(status) || "SUSPENDED".equals(status);
        }

        boolean live() {
            return "IN_PLAY".equals(status) || "PAUSED".equals(status);
        }

        /** 'V', 'N' or 'D' from Barça's side. */
        char result() {
            if ("DRAW".equals(winner)) return 'N';
            boolean homeWon = "HOME_TEAM".equals(winner);
            if (!homeWon && !"AWAY_TEAM".equals(winner)) return goalsFor > goalsAgainst ? 'V' : goalsFor < goalsAgainst ? 'D' : 'N';
            return homeWon == home ? 'V' : 'D';
        }

        String fixture() {
            return home ? "Barça – " + opponent : opponent + " – Barça";
        }
    }

    static final class Row {
        final int position, teamId, played, won, draw, lost, goalDifference, points;
        final String team;

        Row(int position, int teamId, String team, int played, int won, int draw, int lost, int goalDifference, int points) {
            this.position = position;
            this.teamId = teamId;
            this.team = team;
            this.played = played;
            this.won = won;
            this.draw = draw;
            this.lost = lost;
            this.goalDifference = goalDifference;
            this.points = points;
        }
    }
}
