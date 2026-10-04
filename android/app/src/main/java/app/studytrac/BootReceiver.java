package app.studytrac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are cleared by Android on reboot; put them back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Alarms.rescheduleStored(c);
        if (Prefs.lockActive(c)) LockEndReceiver.schedule(c, Prefs.lockUntil(c));
    }
}
