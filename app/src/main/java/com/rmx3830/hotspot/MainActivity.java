package com.rmx3830.hotspot;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("RMX3830 Hotspot Manager");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Управление подключёнными устройствами, лимитом клиентов, "
                + "белым/чёрным списком и временем автоотключения.");
        info.setPadding(0, 24, 0, 24);
        root.addView(info);

        Button open = new Button(this);
        open.setText("ОТКРЫТЬ УПРАВЛЕНИЕ ТОЧКОЙ ДОСТУПА");
        open.setOnClickListener(v -> AdvancedHotspotUi.open(this));
        root.addView(open);

        TextView note = new TextView(this);
        note.setText("\nИзоляция клиентов доступна только если прошивка "
                + "реально предоставляет соответствующий Soft AP API.");
        note.setPadding(0, 24, 0, 0);
        root.addView(note);

        setTitle("RMX3830 Hotspot Manager");
        setContentView(root);
    }
}
