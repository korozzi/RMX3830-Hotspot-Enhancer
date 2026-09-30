package com.rmx3830.hotspot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 10, 12);
    private static final int CARD = Color.rgb(20, 21, 25);
    private static final int CARD_2 = Color.rgb(27, 28, 33);
    private static final int RED = Color.rgb(255, 58, 48);
    private static final int TEXT = Color.rgb(245, 245, 247);
    private static final int MUTED = Color.rgb(165, 167, 174);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(content);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.app_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(icon, new LinearLayout.LayoutParams(dp(68), dp(68)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(16), 0, 0, 0);
        titleBox.addView(text("Hotspot Manager", 27, TEXT, true));
        TextView subtitle = text("Управление точкой доступа", 14, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header);

        TextView status = text("●  Модуль активен", 14, RED, true);
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        status.setBackground(round(CARD_2, 18));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(18);
        content.addView(status, statusLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(round(CARD, 22));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(16);

        card.addView(text("Точка доступа", 18, TEXT, true));
        TextView cardInfo = text("Подключённые устройства, лимит, списки доступа, отключение и изоляция.",
                14, MUTED, false);
        cardInfo.setPadding(0, dp(7), 0, dp(16));
        card.addView(cardInfo);

        TextView open = text("Открыть", 16, Color.WHITE, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(18), dp(14), dp(18), dp(14));
        open.setBackground(round(RED, 16));
        open.setOnClickListener(v -> AdvancedHotspotUi.open(this));
        card.addView(open);
        content.addView(card, cardLp);

        TextView footer = text("Hotspot Manager", 12, Color.rgb(105, 107, 113), false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(24);
        content.addView(footer, footerLp);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        setTitle("Hotspot Manager");
        root.postDelayed(() -> AdvancedHotspotUi.open(this), 120);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
