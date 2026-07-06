package com.sw.openingos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // O Opening OS abre naturalmente quando estiver definido como app HOME padrão.
        // Esta classe fica preparada para recursos futuros de restauração de estado.
    }
}
