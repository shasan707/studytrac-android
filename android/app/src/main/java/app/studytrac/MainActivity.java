package app.studytrac;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(StudyNativePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If a focus session is running and the user came back, re-pin the screen.
        if (Prefs.lockActive(this)) {
            try { startLockTask(); } catch (Exception ignored) {}
        }
    }
}
