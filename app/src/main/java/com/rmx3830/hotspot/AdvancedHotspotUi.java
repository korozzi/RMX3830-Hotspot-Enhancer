package com.rmx3830.hotspot;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

final class AdvancedHotspotUi {
    private static final String TAG = "RMX3830Hotspot";
    private static final long CLIENT_FORCE_DISCONNECT_DELAY_MS = 2500L;

    private AdvancedHotspotUi() {}

    static void open(Context context) { showEditor(context); }

    private static void showEditor(Context context) {
        try {
            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) throw new IllegalStateException("wifi_service_null");
            Object current = invoke(wifi, "getSoftApConfiguration");
            if (current == null) throw new IllegalStateException("softap_config_null");

            int storedClients = ((Number) invoke(current, "getMaxNumberOfClients")).intValue();
            long storedTimeout = ((Number) invoke(current, "getShutdownTimeoutMillis")).longValue();
            boolean storedAuto = (Boolean) invoke(current, "isAutoShutdownEnabled");
            boolean storedIsolation = readBoolean(current, "isClientIsolationEnabled", false);
            boolean clientControl = readBoolean(current, "isClientControlByUserEnabled", false);

            int initialClients = storedClients > 0 ? Math.min(storedClients, 10) : 10;
            int initialTimeout = storedTimeout > 0
                    ? (int) Math.max(1, Math.min(1440, storedTimeout / 60000L))
                    : 10;

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(48, 8, 48, 8);

            TextView clientHeader = label(context, "Подключённые устройства");
            content.addView(clientHeader);

            TextView clientCounter = label(context, "Подключено: … / " + initialClients);
            content.addView(clientCounter);

            LinearLayout clientList = new LinearLayout(context);
            clientList.setOrientation(LinearLayout.VERTICAL);
            content.addView(clientList);

            Button refreshClients = new Button(context);
            refreshClients.setText("Обновить список");
            content.addView(refreshClients);

            Button whitelistButton = new Button(context);
            whitelistButton.setText("Настроить белый список");
            content.addView(whitelistButton);

            Button blockedButton = new Button(context);
            blockedButton.setText("Заблокированные устройства");
            content.addView(blockedButton);

            content.addView(label(context, "Максимум клиентов (1–10)"));
            EditText maxEdit = new EditText(context);
            maxEdit.setSingleLine(true);
            maxEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
            maxEdit.setText(String.valueOf(initialClients));
            content.addView(maxEdit);

            content.addView(label(context, "Отключать после простоя (минуты)"));
            EditText timeoutEdit = new EditText(context);
            timeoutEdit.setSingleLine(true);
            timeoutEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
            timeoutEdit.setText(String.valueOf(initialTimeout));
            content.addView(timeoutEdit);

            CheckBox isolationBox = check(context,
                    "Изоляция клиентов", storedIsolation);
            boolean isolationSupported = hasMethod(current, "isClientIsolationEnabled")
                    && hasBuilderMethod(current, "setClientIsolationEnabled");
            isolationBox.setEnabled(isolationSupported);
            if (!isolationSupported) {
                isolationBox.setText("Изоляция клиентов (не поддерживается этой сборкой)");
            }
            content.addView(isolationBox);

            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.addView(content);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            Button cancel = new Button(context);
            cancel.setText("Отмена");
            Button applyButton = new Button(context);
            applyButton.setText("Применить");
            buttons.addView(cancel);
            buttons.addView(applyButton);
            container.addView(buttons, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("RMX3830 Hotspot")
                    .setView(container)
                    .create();

            ClientCallbackHandle callback = registerClientCallback(
                    wifi, context, clientCounter, clientList, maxEdit);

            Runnable refresh = () -> refreshClientsFromCallback(
                    clientCounter, clientList, maxEdit, callback == null ? null : callback.clients);
            refreshClients.setOnClickListener(v -> {
                try {
                    Object cfg = invoke(wifi, "getSoftApConfiguration");
                    int limit = ((Number) invoke(cfg, "getMaxNumberOfClients")).intValue();
                    if (limit <= 0) limit = 10;
                    clientCounter.setText("Подключено: "
                            + (callback == null ? 0 : callback.clients.size()) + " / " + limit);
                    refresh.run();
                } catch (Throwable t) {
                    Log.i(TAG, "SETTINGS_CLIENT_REFRESH_FAILED " + t.getClass().getSimpleName());
                }
            });

            whitelistButton.setOnClickListener(v -> showWhitelistEditor(context, wifi));
            blockedButton.setOnClickListener(v -> showBlockedEditor(context, wifi));

            cancel.setOnClickListener(v -> {
                unregisterClientCallback(wifi, callback);
                dialog.dismiss();
            });

            applyButton.setOnClickListener(v -> {
                try {
                    int clients = Integer.parseInt(maxEdit.getText().toString().trim());
                    int timeoutMinutes = Integer.parseInt(timeoutEdit.getText().toString().trim());
                    if (clients < 1 || clients > 10) {
                        maxEdit.setError("1–10");
                        return;
                    }
                    if (timeoutMinutes < 1 || timeoutMinutes > 1440) {
                        timeoutEdit.setError("1–1440 минут");
                        return;
                    }

                    Object config = invoke(wifi, "getSoftApConfiguration");
                    if (config == null) throw new IllegalStateException("softap_config_null");
                    apply(wifi, config, clients, timeoutMinutes,
                            isolationSupported && isolationBox.isChecked());

                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> verifyAndLog(wifi, clients, timeoutMinutes,
                                    isolationSupported && isolationBox.isChecked()), 500);
                    unregisterClientCallback(wifi, callback);
                    dialog.dismiss();
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    Log.i(TAG, "SETTINGS_ADVANCED_APPLY_FAILED "
                            + cause.getClass().getSimpleName() + ": "
                            + String.valueOf(cause.getMessage()));
                    maxEdit.setError("Не удалось применить");
                }
            });

            dialog.setOnDismissListener(d -> unregisterClientCallback(wifi, callback));
            dialog.show();
            Log.i(TAG, "SETTINGS_ADVANCED_DIALOG_SHOWN");
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            Log.i(TAG, "SETTINGS_ADVANCED_DIALOG_FAILED "
                    + cause.getClass().getSimpleName() + ": "
                    + String.valueOf(cause.getMessage()));
        }
    }

    private static void refreshClientsFromCallback(TextView counter, LinearLayout list,
            EditText maxEdit, List<Object> clients) {
        if (clients == null) return;
        list.removeAllViews();
        int limit = parseInt(maxEdit.getText().toString(), 10);
        counter.setText("Подключено: " + clients.size() + " / " + limit);
        for (Object client : new ArrayList<>(clients)) {
            String mac = clientMac(client);
            LinearLayout row = new LinearLayout(counter.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 8, 0, 8);

            TextView info = new TextView(counter.getContext());
            info.setText("📱 " + mac);
            row.addView(info);

            LinearLayout actions = new LinearLayout(counter.getContext());
            actions.setGravity(Gravity.END);
            Button disconnect = new Button(counter.getContext());
            disconnect.setText("Отключить");
            Button allow = new Button(counter.getContext());
            allow.setText("Белый список");
            Button block = new Button(counter.getContext());
            block.setText("Заблокировать");
            actions.addView(disconnect);
            actions.addView(allow);
            actions.addView(block);
            row.addView(actions);
            list.addView(row);

            disconnect.setOnClickListener(v -> forceTemporaryDisconnect(
                    counter.getContext(), clientMac(client)));
            allow.setOnClickListener(v -> allowClient(
                    counter.getContext(), clientMac(client)));
            block.setOnClickListener(v -> blockClient(
                    counter.getContext(), clientMac(client)));
        }
    }

    private static void showWhitelistEditor(Context context, Object wifi) {
        try {
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            boolean enabled = readBoolean(cfg, "isClientControlByUserEnabled", false);

            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(48, 8, 48, 8);

            CheckBox enable = check(context, "Белый список включён", enabled);
            box.addView(enable);
            box.addView(label(context,
                    "При включении подключаться смогут только MAC-адреса из разрешённого списка."));

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            Runnable render = () -> renderMacList(context, list, allowed, "Разрешено");
            render.run();

            Button add = new Button(context);
            add.setText("Добавить MAC-адрес");
            box.addView(add);

            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Белый список")
                    .setView(box)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Применить", null)
                    .create();

            add.setOnClickListener(v -> askForMac(context, mac -> {
                if (!containsMac(allowed, mac)) {
                    allowed.add(parseMac(mac));
                    render.run();
                }
            }));

            dialog.setOnShowListener(d -> {
                Button applyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                applyButton.setOnClickListener(v -> {
                    try {
                        applyClientLists(wifi, enabledValue(enable), allowed,
                                copyList(invoke(cfg, "getBlockedClientList")));
                        dialog.dismiss();
                    } catch (Throwable t) {
                        showError(context, "Белый список", t);
                    }
                });
            });
            dialog.show();
        } catch (Throwable t) {
            showError(context, "Белый список", t);
        }
    }

    private static void showBlockedEditor(Context context, Object wifi) {
        try {
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> blocked = copyList(invoke(cfg, "getBlockedClientList"));

            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(48, 8, 48, 8);
            box.addView(label(context,
                    "Заблокированные устройства не смогут подключиться к точке доступа."));

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            Runnable render = () -> renderMacList(context, list, blocked, "Заблокировано");
            render.run();

            Button add = new Button(context);
            add.setText("Добавить MAC-адрес");
            box.addView(add);

            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Заблокированные устройства")
                    .setView(box)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Применить", null)
                    .create();

            add.setOnClickListener(v -> askForMac(context, mac -> {
                if (!containsMac(blocked, mac)) {
                    blocked.add(parseMac(mac));
                    render.run();
                }
            }));

            dialog.setOnShowListener(d -> {
                Button applyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                applyButton.setOnClickListener(v -> {
                    try {
                        List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
                        allowed.removeIf(x -> containsMac(blocked, macString(x)));
                        applyClientLists(wifi,
                                readBoolean(cfg, "isClientControlByUserEnabled", false),
                                allowed, blocked);
                        dialog.dismiss();
                    } catch (Throwable t) {
                        showError(context, "Блокировка", t);
                    }
                });
            });
            dialog.show();
        } catch (Throwable t) {
            showError(context, "Блокировка", t);
        }
    }

    private static void renderMacList(Context context, LinearLayout list,
            List<Object> macs, String prefix) {
        list.removeAllViews();
        if (macs.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Список пуст");
            list.addView(empty);
            return;
        }
        for (Object mac : new ArrayList<>(macs)) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView text = new TextView(context);
            text.setText(prefix + ": " + macString(mac));
            row.addView(text, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(row);
        }
    }

    private static void askForMac(Context context, MacConsumer consumer) {
        EditText edit = new EditText(context);
        edit.setSingleLine(true);
        edit.setHint("AA:BB:CC:DD:EE:FF");
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(context)
                .setTitle("MAC-адрес")
                .setView(edit)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", (d, which) -> {
                    String mac = edit.getText().toString().trim();
                    if (!isMac(mac)) {
                        edit.setError("Формат AA:BB:CC:DD:EE:FF");
                        return;
                    }
                    consumer.accept(mac.toUpperCase());
                }).show();
    }

    private static void allowClient(Context context, String mac) {
        try {
            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> blocked = copyList(invoke(cfg, "getBlockedClientList"));
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            blocked.removeIf(x -> mac.equalsIgnoreCase(macString(x)));
            if (!containsMac(allowed, mac)) allowed.add(parseMac(mac));
            applyClientLists(wifi, true, allowed, blocked);
            Log.i(TAG, "CLIENT_ALLOWED mac=" + mac);
        } catch (Throwable t) {
            showError(context, "Белый список", t);
        }
    }

    private static void blockClient(Context context, String mac) {
        try {
            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> blocked = copyList(invoke(cfg, "getBlockedClientList"));
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            if (!containsMac(blocked, mac)) blocked.add(parseMac(mac));
            allowed.removeIf(x -> containsMac(blocked, macString(x)));
            applyClientLists(wifi,
                    readBoolean(cfg, "isClientControlByUserEnabled", false),
                    allowed, blocked);
            Log.i(TAG, "CLIENT_BLOCKED mac=" + mac);
        } catch (Throwable t) {
            showError(context, "Блокировка", t);
        }
    }

    private static void forceTemporaryDisconnect(Context context, String mac) {
        try {
            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> blocked = copyList(invoke(cfg, "getBlockedClientList"));
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            boolean control = readBoolean(cfg, "isClientControlByUserEnabled", false);
            if (!containsMac(blocked, mac)) blocked.add(parseMac(mac));
            applyClientLists(wifi, control, allowed, blocked);
            Log.i(TAG, "CLIENT_FORCE_DISCONNECT_REQUEST mac=" + mac);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Object latest = invoke(wifi, "getSoftApConfiguration");
                    List<Object> latestBlocked = copyList(invoke(latest, "getBlockedClientList"));
                    latestBlocked.removeIf(x -> mac.equalsIgnoreCase(macString(x)));
                    List<Object> latestAllowed = copyList(invoke(latest, "getAllowedClientList"));
                    applyClientLists(wifi,
                            readBoolean(latest, "isClientControlByUserEnabled", false),
                            latestAllowed, latestBlocked);
                    Log.i(TAG, "CLIENT_FORCE_DISCONNECT_RELEASED mac=" + mac);
                } catch (Throwable t) {
                    Log.i(TAG, "CLIENT_FORCE_DISCONNECT_RELEASE_FAILED "
                            + t.getClass().getSimpleName());
                }
            }, CLIENT_FORCE_DISCONNECT_DELAY_MS);
        } catch (Throwable t) {
            showError(context, "Отключение клиента", t);
        }
    }

    private static void applyClientLists(Object wifi, boolean control,
            List<Object> allowed, List<Object> blocked) throws Exception {
        Object current = invoke(wifi, "getSoftApConfiguration");
        Class<?> builderClass = Class.forName(
                "android.net.wifi.SoftApConfiguration$Builder", false,
                current.getClass().getClassLoader());
        Constructor<?> ctor = builderClass.getConstructor(current.getClass());
        Object builder = ctor.newInstance(current);
        call(builder, "setClientControlByUserEnabled",
                new Class<?>[]{boolean.class}, control);
        call(builder, "setAllowedClientList", new Class<?>[]{List.class}, allowed);
        call(builder, "setBlockedClientList", new Class<?>[]{List.class}, blocked);
        setSoftApConfiguration(wifi, invoke(builder, "build"));
        Log.i(TAG, "CLIENT_LISTS_APPLIED control=" + control
                + " allowed=" + allowed.size() + " blocked=" + blocked.size());
    }

    private static void apply(Object wifi, Object current, int clients,
            int timeoutMinutes, boolean isolation) throws Exception {
        Class<?> builderClass = Class.forName(
                "android.net.wifi.SoftApConfiguration$Builder", false,
                current.getClass().getClassLoader());
        Constructor<?> ctor = builderClass.getConstructor(current.getClass());
        Object builder = ctor.newInstance(current);

        call(builder, "setMaxNumberOfClients", new Class<?>[]{int.class}, clients);
        call(builder, "setShutdownTimeoutMillis", new Class<?>[]{long.class},
                timeoutMinutes * 60_000L);

        setSoftApConfiguration(wifi, invoke(builder, "build"));
        Log.i(TAG, "SETTINGS_ADVANCED_APPLIED clients=" + clients
                + " timeoutMin=" + timeoutMinutes);

        if (hasBuilderMethod(current, "setClientIsolationEnabled")) {
            try {
                Object isolationBuilder = ctor.newInstance(invoke(wifi, "getSoftApConfiguration"));
                call(isolationBuilder, "setClientIsolationEnabled",
                        new Class<?>[]{boolean.class}, isolation);
                setSoftApConfiguration(wifi, invoke(isolationBuilder, "build"));
                Log.i(TAG, "SETTINGS_ISOLATION_APPLIED isolation=" + isolation);
            } catch (Throwable isolationError) {
                Log.i(TAG, "SETTINGS_ISOLATION_UNAVAILABLE "
                        + isolationError.getClass().getSimpleName());
            }
        } else {
            Log.i(TAG, "SETTINGS_ISOLATION_UNAVAILABLE method_missing");
        }
    }

    private static void setSoftApConfiguration(Object wifi, Object config) throws Exception {
        Method setter = findMethod(wifi.getClass(), "setSoftApConfiguration", 1);
        if (setter == null) throw new NoSuchMethodException("setSoftApConfiguration");
        setter.setAccessible(true);
        Object returned = setter.invoke(wifi, config);
        if (returned instanceof Boolean && !((Boolean) returned)) {
            throw new IllegalStateException("setSoftApConfiguration returned false");
        }
    }

    private static void verifyAndLog(Object wifi, int expectedClients,
            int expectedTimeoutMinutes, boolean expectedIsolation) {
        try {
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            int clients = ((Number) invoke(cfg, "getMaxNumberOfClients")).intValue();
            long timeout = ((Number) invoke(cfg, "getShutdownTimeoutMillis")).longValue();
            boolean isolation = readBoolean(cfg, "isClientIsolationEnabled", false);
            Log.i(TAG, "SETTINGS_ADVANCED_VERIFY clients=" + clients
                    + " timeoutMs=" + timeout
                    + " isolation=" + isolation
                    + " expectedClients=" + expectedClients
                    + " expectedTimeoutMs=" + (expectedTimeoutMinutes * 60000L)
                    + " expectedIsolation=" + expectedIsolation);
            if (clients != expectedClients
                    || timeout != expectedTimeoutMinutes * 60000L
                    || (hasMethod(cfg, "isClientIsolationEnabled")
                        && isolation != expectedIsolation)) {
                Log.i(TAG, "SETTINGS_ADVANCED_VERIFY_MISMATCH");
            }
        } catch (Throwable t) {
            Log.i(TAG, "SETTINGS_ADVANCED_VERIFY_FAILED "
                    + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    private static ClientCallbackHandle registerClientCallback(Object wifi, Context context,
            TextView counter, LinearLayout list, EditText maxEdit) {
        try {
            Class<?> callbackClass = Class.forName("android.net.wifi.WifiManager$SoftApCallback");
            final ClientCallbackHandle handle = new ClientCallbackHandle();
            Executor executor = command -> new Handler(Looper.getMainLooper()).post(command);

            Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                        if ("onConnectedClientsChanged".equals(method.getName()) && args != null) {
                            for (Object arg : args) {
                                if (arg instanceof List) {
                                    handle.clients = new ArrayList<>((List<Object>) arg);
                                    refreshClientsFromCallback(counter, list, maxEdit, handle.clients);
                                }
                            }
                        }
                        return null;
                    });

            Method register = findMethod(wifi.getClass(), "registerSoftApCallback", 2);
            if (register == null) throw new NoSuchMethodException("registerSoftApCallback");
            register.setAccessible(true);
            register.invoke(wifi, executor, callback);
            handle.callback = callback;
            Log.i(TAG, "CLIENT_CALLBACK_REGISTERED");
            return handle;
        } catch (Throwable t) {
            Log.i(TAG, "CLIENT_CALLBACK_FAILED " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()));
            counter.setText("Подключено: недоступно");
            return null;
        }
    }

    private static void unregisterClientCallback(Object wifi, ClientCallbackHandle handle) {
        if (handle == null || handle.callback == null) return;
        try {
            Method unregister = findMethod(wifi.getClass(), "unregisterSoftApCallback", 1);
            if (unregister != null) {
                unregister.setAccessible(true);
                unregister.invoke(wifi, handle.callback);
            }
            handle.callback = null;
            Log.i(TAG, "CLIENT_CALLBACK_UNREGISTERED");
        } catch (Throwable t) {
            Log.i(TAG, "CLIENT_CALLBACK_UNREGISTER_FAILED "
                    + t.getClass().getSimpleName());
        }
    }

    private static String clientMac(Object client) {
        try {
            return macString(invoke(client, "getMacAddress"));
        } catch (Throwable t) {
            return String.valueOf(client);
        }
    }

    private static String macString(Object mac) {
        return String.valueOf(mac);
    }

    private static Object parseMac(String mac) {
        try {
            Class<?> macClass = Class.forName("android.net.MacAddress");
            Method fromString = macClass.getMethod("fromString", String.class);
            return fromString.invoke(null, mac);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid MAC: " + mac, t);
        }
    }

    private static boolean isMac(String mac) {
        return mac != null && mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    }

    private static boolean containsMac(List<Object> list, String mac) {
        for (Object item : list) {
            if (mac.equalsIgnoreCase(macString(item))) return true;
        }
        return false;
    }

    private static List<Object> copyList(Object value) {
        List<Object> out = new ArrayList<>();
        if (value instanceof List) out.addAll((List<Object>) value);
        return out;
    }

    private static boolean enabledValue(CheckBox box) {
        return box.isChecked();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean readBoolean(Object target, String name, boolean fallback) {
        try { return (Boolean) invoke(target, name); }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean hasMethod(Object target, String name) {
        return findMethod(target.getClass(), name, 0) != null;
    }

    private static boolean hasBuilderMethod(Object config, String name) {
        try {
            Class<?> builder = Class.forName(
                    "android.net.wifi.SoftApConfiguration$Builder", false,
                    config.getClass().getClassLoader());
            for (Method m : builder.getDeclaredMethods()) {
                if (m.getName().equals(name)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void showError(Context context, String title, Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        Log.i(TAG, title + "_FAILED " + cause.getClass().getSimpleName()
                + ": " + String.valueOf(cause.getMessage()));
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage("Не удалось применить настройку: "
                        + cause.getClass().getSimpleName())
                .setPositiveButton("OK", null)
                .show();
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

    private interface MacConsumer { void accept(String mac); }

    private static final class ClientCallbackHandle {
        Object callback;
        List<Object> clients = new ArrayList<>();
    }
}
