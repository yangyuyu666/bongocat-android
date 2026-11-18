package com.yuyu.bongocat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PetTimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PetToolsStore store = new PetToolsStore(context);
        if (!store.token().equals(intent.getStringExtra("token"))) return;
        if (PetReminder.ALARM.equals(intent.getAction())) {
            PetReminder.checkDue(context, store.token());
            if (store.status() == PetToolsStore.RUNNING) PetReminder.schedule(context);
        } else if (PetReminder.ACK.equals(intent.getAction())) PetReminder.acknowledge(context);
    }
}
