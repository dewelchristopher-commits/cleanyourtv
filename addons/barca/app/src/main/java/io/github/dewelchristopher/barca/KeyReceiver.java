package io.github.dewelchristopher.barca;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The football-data.org key, sent once from the computer:
 * {@code am broadcast -n io.github.dewelchristopher.barca/.KeyReceiver -a io.github.dewelchristopher.barca.SET_KEY --es key <key>}.
 * Only the ADB shell holds the permission the manifest asks of the sender. The first refresh starts at once.
 */
public final class KeyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("key");
        if (key == null || key.trim().isEmpty()) {
            setResultData("pas de cle : ajoute --es key <ta cle>");
            return;
        }
        Store store = new Store(context);
        store.setKey(key);
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                store.refresh();
                app.getContentResolver().notifyChange(GamesProvider.TURNS, null);
                String error = store.error();
                pending.setResultData(error == null
                        ? "OK : " + store.matches().size() + " matchs, " + store.table().size() + " equipes au classement"
                        : "cle enregistree, mais : " + error);
            } finally {
                pending.finish();
            }
        }, "barca-key").start();
    }
}
