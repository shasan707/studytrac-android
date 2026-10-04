package app.studytrac;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** The ringing screen: shows over the lock screen, plays the alarm sound, offers Open, Snooze and Dismiss. */
public class AlarmActivity extends Activity {
    private MediaPlayer player;
    private Vibrator vib;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        String id = getIntent().getStringExtra("id"), title = getIntent().getStringExtra("title"), body = getIntent().getStringExtra("body");
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setBackgroundColor(Color.parseColor("#1a221d")); root.setPadding((int) (28 * d), 0, (int) (28 * d), 0);
        TextView icon = new TextView(this); icon.setText("⏰"); icon.setTextSize(64); icon.setGravity(Gravity.CENTER);
        TextView t = new TextView(this); t.setText(title); t.setTextColor(Color.WHITE); t.setTextSize(26); t.setTypeface(Typeface.DEFAULT_BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0, (int) (16 * d), 0, (int) (8 * d));
        TextView s = new TextView(this); s.setText(body); s.setTextColor(Color.parseColor("#b3bdb6")); s.setTextSize(16); s.setGravity(Gravity.CENTER); s.setPadding(0, 0, 0, (int) (32 * d));
        Button open = button("Open Study Tracker", "#6fcf93", "#0f1a13", d), snooze = button("Snooze 10 minutes", "#2b3630", "#e9eee9", d), dismiss = button("Dismiss", "#2b3630", "#e9eee9", d);
        root.addView(icon); root.addView(t); root.addView(s); root.addView(open); root.addView(snooze); root.addView(dismiss);
        setContentView(root);

        open.setOnClickListener(v -> { stop(id); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
        snooze.setOnClickListener(v -> {
            stop(id);
            try { JSONObject o = new JSONObject(); o.put("id", id + "_snooze"); o.put("at", System.currentTimeMillis() + 10 * 60_000L); o.put("title", title); o.put("body", body); Alarms.schedule(this, o); } catch (Exception ignored) {}
            finish();
        });
        dismiss.setOnClickListener(v -> { stop(id); finish(); });
        ring();
    }

    private Button button(String text, String bg, String fg, float d) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(Color.parseColor(fg)); b.setBackgroundColor(Color.parseColor(bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (54 * d)); lp.topMargin = (int) (10 * d); b.setLayoutParams(lp);
        return b;
    }

    private void ring() {
        try {
            player = new MediaPlayer();
            player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setLooping(true); player.prepare(); player.start();
        } catch (Exception ignored) {}
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {0, 600, 400};
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pattern, 0)); else vib.vibrate(pattern, 0);
        } catch (Exception ignored) {}
        // Stop by itself after three minutes so a missed alarm does not ring forever.
        new android.os.Handler(getMainLooper()).postDelayed(() -> { stop(getIntent().getStringExtra("id")); finish(); }, 3 * 60_000L);
    }

    private void stop(String id) {
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        try { if (vib != null) vib.cancel(); } catch (Exception ignored) {}
        try { if (id != null) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(id.hashCode()); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() { stop(null); super.onDestroy(); }
    @Override public void onBackPressed() { /* use the buttons */ }
}
