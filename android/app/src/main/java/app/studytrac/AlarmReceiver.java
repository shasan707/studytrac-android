package app.studytrac;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** Fires when an alarm time arrives: posts a full-screen, alarm-sound notification and opens the ringing screen. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id"), title = intent.getStringExtra("title"), body = intent.getStringExtra("body");
        if (title == null) title = "Study Tracker";
        if (body == null) body = "A review is due.";
        Alarms.ensureChannel(c);

        Intent full = new Intent(c, AlarmActivity.class).putExtra("id", id).putExtra("title", title).putExtra("body", body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent fullPi = PendingIntent.getActivity(c, (id + "f").hashCode(), full, flags);
        PendingIntent openPi = PendingIntent.getActivity(c, (id + "o").hashCode(), new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags);

        Notification n = new NotificationCompat.Builder(c, Alarms.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullPi, true).setContentIntent(openPi).setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 300, 500}).build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id.hashCode(), n);

        // Full-screen intents are honoured when the screen is off; when it is on, open the ringing screen ourselves.
        try { c.startActivity(full); } catch (Exception ignored) {}
    }
}
