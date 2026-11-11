package dev.pranav.applock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Lives in the instrumentation APK, separate from the application under test. */
public class ShortcutTestActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView label = new TextView(this);
        label.setText("Shortcut navigation test");
        setContentView(label);
    }
}
