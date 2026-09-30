package com.rmx3830.hotspot;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class AdvancedHotspotUi {
    private static final String TAG = "RMX3830Hotspot";
    private static final String KEY = "rmx3830_hotspot_advanced";
    // BAND_* fields are public only starting with API 36; this project targets API 35.
    // Their stable framework values are 1 (2 GHz) and 2 (5 GHz).
    private static final int BAND_2GHZ = 1;
    private static final int BAND_5GHZ = 2;

    private AdvancedHotspotUi() {}

    static void inject(Object fragment) {
        try {
            Context context = (Context) invoke(fragment, "getContext");
            if (context == null) return;
            Object screen = invoke(fragment, "getPreferenceScreen");
            if (screen == null) return;
            Object existing = invoke(screen, "findPreference", KEY);
            if (existing != null) return;

            ClassLoader cl = fragment.getClass().getClassLoader();
            Class<?> prefClass = Class.forName("androidx.preference.Preference", false, cl);
            Constructor<?> ctor = prefClass.getConstructor(Context.class);
            Object pref = ctor.newInstance(context);
            invoke(pref, "setKey", KEY);
            invoke(pref, "setTitle", "Расширенные настройки точки доступа");
            invoke(pref, "setSummary",
                    "Лимит клиентов, диапазон, скрытая сеть, автоотключение и изоляция");

            Class<?> listener = Class.forName(
                    "androidx.preference.Preference$OnPreferenceClickListener", false, cl);
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{listener},
                    (p, method, args) -> {
                        if ("onPreferenceClick".equals(method.getName())) {
                            showEditor(context);
                            return true;
                        }
                        return null;
                    });
            invoke(pref, "setOnPreferenceClickListener", proxy);
            invoke(screen, "addPreference", pref);
            Log.i(TAG, "SETTINGS_ADVANCED_UI_ADDED");
        } catch (Throwable t) {
            Log.i(TAG, "SETTINGS_ADVANCED_UI_FAILED " + t.getClass().getSimpleName());
        }
    }

    private static void showEditor(Context context) {
        try {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(48, 8, 48, 4);

            root.addView(label(context, "Максимум клиентов (1–10)"));
            EditText maxEdit = new EditText(context);
            maxEdit.setSingleLine(true);
            maxEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
            maxEdit.setText("10");
            root.addView(maxEdit);

            CheckBox autoBox = check(context, "Автоотключение при простое", true);
            root.addView(autoBox);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("RMX3830 Hotspot")
                    .setView(root)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Применить", null)
                    .create();

            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        try {
                            int clients = Integer.parseInt(maxEdit.getText().toString().trim());
                            if (clients < 1 || clients > 10) {
                                maxEdit.setError("1–10");
                                return;
                            }

                            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
                            if (wifi == null) throw new IllegalStateException("wifi_service_null");
                            Object config = invoke(wifi, "getSoftApConfiguration");
                            if (config == null) throw new IllegalStateException("softap_config_null");

                            apply(wifi, config, clients, autoBox.isChecked());
                            dialog.dismiss();
                        } catch (Throwable t) {
                            Throwable cause = t.getCause() != null ? t.getCause() : t;
                            Log.i(TAG, "SETTINGS_ADVANCED_APPLY_FAILED "
                                    + cause.getClass().getSimpleName() + ": "
                                    + String.valueOf(cause.getMessage()));
                            maxEdit.setError("Не удалось применить");
                        }
                    }));
            dialog.show();
            Log.i(TAG, "SETTINGS_ADVANCED_DIALOG_SHOWN");
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            Log.i(TAG, "SETTINGS_ADVANCED_DIALOG_FAILED "
                    + cause.getClass().getSimpleName() + ": "
                    + String.valueOf(cause.getMessage()));
        }
    }

    private static void apply(Object wifi, Object current, int clients,
            boolean autoOff) throws Exception {
        Class<?> configClass = current.getClass();
        Class<?> builderClass = Class.forName(
                "android.net.wifi.SoftApConfiguration$Builder", false,
                configClass.getClassLoader());
        Constructor<?> ctor = builderClass.getConstructor(configClass);
        Object builder = ctor.newInstance(current);

        // These two operations are explicitly supported by WifiManager while a
        // tethered Soft AP is running. Do not touch SSID, password, security,
        // band, hidden SSID, or Realme's compatibility setting.
        call(builder, "setMaxNumberOfClients", new Class<?>[]{int.class}, clients);
        call(builder, "setAutoShutdownEnabled", new Class<?>[]{boolean.class}, autoOff);

        Object result = invoke(builder, "build");
        Method setter = findMethod(wifi.getClass(), "setSoftApConfiguration", 1);
        if (setter == null) throw new NoSuchMethodException("setSoftApConfiguration");
        setter.setAccessible(true);
        Object returned = setter.invoke(wifi, result);

        if (returned instanceof Boolean && !((Boolean) returned)) {
            throw new IllegalStateException("setSoftApConfiguration returned false");
        }

        Log.i(TAG, "SETTINGS_ADVANCED_APPLIED clients=" + clients
                + " autoOff=" + autoOff);
    }


    private static TextView label(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setPadding(0, 12, 0, 2);
        return v;
    }

    private static CheckBox check(Context c, String text, boolean value) {
        CheckBox v = new CheckBox(c);
        v.setText(text);
        v.setChecked(value);
        return v;
    }

    private static RadioButton radio(Context c, String text, int id) {
        RadioButton v = new RadioButton(c);
        v.setText(text);
        v.setId(id);
        return v;
    }

    private static Object call(Object target, String name, Class<?>[] types, Object arg)
            throws Exception {
        Method m = target.getClass().getMethod(name, types);
        return m.invoke(target, arg);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method m = findCompatibleMethod(target.getClass(), name, args);
        if (m == null) throw new NoSuchMethodException(name);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Method findMethod(Class<?> cls, String name, int parameterCount) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)
                        && m.getParameterTypes().length == parameterCount) return m;
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> cls, String name, Object[] args) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)
                        || m.getParameterTypes().length != args.length) continue;
                Class<?>[] p = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    if (args[i] == null) continue;
                    if (!wrap(p[i]).isAssignableFrom(wrap(args[i].getClass()))) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return m;
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }
}
