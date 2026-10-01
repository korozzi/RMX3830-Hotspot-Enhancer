package com.rmx3830.hotspot;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class AdvancedHotspotUi {
    private static final String TAG = "RMX3830Hotspot";
    private static final long CLIENT_FORCE_DISCONNECT_DELAY_MS = 2500L;
    private static final Map<String, String> CLIENT_NAMES = new HashMap<>();

    private AdvancedHotspotUi() {}

    private static final String PREF_KEY = "rmx3830_hotspot_advanced";

    static void inject(Object fragment) {
        if (fragment == null) return;
        try {
            Context context = (Context) invoke(fragment, "getContext");
            if (context == null) return;
            ClassLoader loader = fragment.getClass().getClassLoader();
            Class<?> preferenceClass = Class.forName(
                    "androidx.preference.Preference", false, loader);
            Object screen = invoke(fragment, "getPreferenceScreen");
            if (screen == null) return;
            Object existing = invoke(screen, "findPreference", PREF_KEY);
            if (existing != null) return;

            Constructor<?> ctor = preferenceClass.getConstructor(Context.class);
            Object pref = ctor.newInstance(context);
            invoke(pref, "setKey", PREF_KEY);
            invoke(pref, "setTitle", "Расширенные настройки точки доступа");
            invoke(pref, "setSummary",
                    "Лимит клиентов, устройства, списки доступа, отключение и изоляция");
            invoke(pref, "setOrder", 999);

            Class<?> listenerClass = Class.forName(
                    "androidx.preference.Preference$OnPreferenceClickListener", false, loader);
            Object listener = Proxy.newProxyInstance(loader,
                    new Class<?>[] { listenerClass },
                    (proxy, method, args) -> {
                        if ("onPreferenceClick".equals(method.getName())) {
                            showEditor(context);
                            return true;
                        }
                        return null;
                    });
            invoke(pref, "setOnPreferenceClickListener", listener);
            Object added = invoke(screen, "addPreference", pref);
            note("SETTINGS_UI_ADDED result=" + String.valueOf(added));
        } catch (Throwable t) {
            note("SETTINGS_UI_INJECT_FAILED " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()));
        }
    }

    private static void note(String message) {
        try { Log.i(TAG, message); } catch (Throwable ignored) { }
    }

    private static void showEditor(Context context) {
        try {
            Object wifi = context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) throw new IllegalStateException("wifi_service_null");
            Object current = invoke(wifi, "getSoftApConfiguration");
            if (current == null) throw new IllegalStateException("softap_config_null");

            int storedClients = ((Number) invoke(current, "getMaxNumberOfClients")).intValue();
            long storedTimeout = ((Number) invoke(current, "getShutdownTimeoutMillis")).longValue();
            boolean storedIsolation = readBoolean(current, "isClientIsolationEnabled", false);
            int initialClients = storedClients > 0 ? Math.min(storedClients, 10) : 10;
            int initialTimeout = storedTimeout > 0
                    ? (int) Math.max(1, Math.min(1440, storedTimeout / 60000L)) : 10;

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(28, 24, 28, 12);
            root.setBackground(roundBackground(0xFFFFFFFF, 28));

            TextView title = rowTitle(context, "Расширенные настройки точки доступа");
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setPadding(0, 0, 0, 18);
            root.addView(title);

            ScrollView scroll = new ScrollView(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);

            content.addView(sectionLabel(context, "Подключённые устройства"));
            LinearLayout clientsCard = card(context);
            TextView clientCounter = rowTitle(context, "Подключено: … / " + initialClients);
            clientCounter.setPadding(0, 10, 0, 10);
            clientsCard.addView(clientCounter);
            LinearLayout clientList = new LinearLayout(context);
            clientList.setOrientation(LinearLayout.VERTICAL);
            clientsCard.addView(clientList);
            TextView refreshClients = actionRow(context, "Обновить список",
                    "Показать текущее состояние точки доступа");
            clientsCard.addView(refreshClients);
            content.addView(clientsCard);

            content.addView(sectionLabel(context, "Управление устройствами"));
            TextView whitelistButton = actionRow(context, "Белый список",
                    "Устройства, которым разрешено подключаться");
            TextView blockedButton = actionRow(context, "Заблокированные устройства",
                    "Устройства, которым запрещено подключение");
            content.addView(whitelistButton);
            content.addView(blockedButton);

            content.addView(sectionLabel(context, "Дополнительные параметры"));
            LinearLayout settingsCard = card(context);
            LinearLayout maxRow = valueRow(context, "Максимум клиентов", "1–10", initialClients);
            LinearLayout timeoutRow = valueRow(context, "Отключать после простоя", "минуты", initialTimeout);
            settingsCard.addView(maxRow);
            settingsCard.addView(timeoutRow);
            EditText maxEdit = (EditText) maxRow.getChildAt(1);
            EditText timeoutEdit = (EditText) timeoutRow.getChildAt(1);

            boolean isolationSupported = hasMethod(current, "isClientIsolationEnabled")
                    && hasBuilderMethod(current, "setClientIsolationEnabled");
            LinearLayout isolationRow = new LinearLayout(context);
            isolationRow.setOrientation(LinearLayout.HORIZONTAL);
            isolationRow.setGravity(Gravity.CENTER_VERTICAL);
            isolationRow.setPadding(0, 16, 0, 16);
            LinearLayout isolationTexts = new LinearLayout(context);
            isolationTexts.setOrientation(LinearLayout.VERTICAL);
            isolationTexts.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            isolationTexts.addView(rowTitle(context, "Изоляция клиентов"));
            isolationTexts.addView(rowSummary(context, isolationSupported
                    ? "Клиенты не смогут обмениваться трафиком напрямую"
                    : "Недоступно в текущей прошивке"));
            isolationRow.addView(isolationTexts);
            Switch isolationSwitch = new Switch(context);
            isolationSwitch.setChecked(storedIsolation);
            isolationSwitch.setEnabled(isolationSupported);
            isolationRow.addView(isolationSwitch);
            settingsCard.addView(isolationRow);
            content.addView(settingsCard);

            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout bottom = new LinearLayout(context);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            TextView cancel = dialogButton(context, "ОТМЕНА");
            TextView applyButton = dialogButton(context, "ПРИМЕНИТЬ");
            bottom.addView(cancel);
            bottom.addView(applyButton);
            root.addView(bottom);

            AlertDialog dialog = new AlertDialog.Builder(context).setView(root).create();
            ClientCallbackHandle callback = registerClientCallback(
                    wifi, context, clientCounter, clientList, maxEdit);

            refreshClients.setOnClickListener(v ->
                    refreshClientsFromCallback(clientCounter, clientList, maxEdit,
                            callback == null ? null : callback.clients, callback));
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
                    apply(wifi, config, clients, timeoutMinutes,
                            isolationSupported && isolationSwitch.isChecked());
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> verifyAndLog(wifi, clients, timeoutMinutes,
                                    isolationSupported && isolationSwitch.isChecked()), 500);
                    unregisterClientCallback(wifi, callback);
                    dialog.dismiss();
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    note("SETTINGS_ADVANCED_APPLY_FAILED "
                            + cause.getClass().getSimpleName() + ": "
                            + String.valueOf(cause.getMessage()));
                    maxEdit.setError("Не удалось применить");
                }
            });

            dialog.setOnDismissListener(d -> unregisterClientCallback(wifi, callback));
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94f),
                        (int) (context.getResources().getDisplayMetrics().heightPixels * 0.86f));
            }
            note("SETTINGS_ADVANCED_DIALOG_SHOWN");
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            note("SETTINGS_ADVANCED_DIALOG_FAILED "
                    + cause.getClass().getSimpleName() + ": "
                    + String.valueOf(cause.getMessage()));
        }
    }

    private static void refreshClientsFromCallback(TextView counter, LinearLayout list,
            EditText maxEdit, List<Object> clients, ClientCallbackHandle handle) {
        if (clients == null) return;
        list.removeAllViews();
        int limit = parseInt(maxEdit.getText().toString(), 10);
        counter.setText("Подключено: " + clients.size() + " / " + limit);
        for (Object client : new ArrayList<>(clients)) {
            String mac = clientMac(client);
            LinearLayout row = new LinearLayout(counter.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 12, 0, 12);
            row.addView(rowTitle(counter.getContext(), deviceName(mac)));
            row.addView(rowSummary(counter.getContext(), mac));

            LinearLayout actions = new LinearLayout(counter.getContext());
            actions.setGravity(Gravity.END);
            TextView disconnect = smallAction(counter.getContext(), "Отключить");
            TextView allow = smallAction(counter.getContext(), "В белый список");
            TextView block = smallAction(counter.getContext(), "Заблокировать");
            actions.addView(disconnect);
            actions.addView(allow);
            actions.addView(block);
            row.addView(actions);
            list.addView(row);

            disconnect.setOnClickListener(v -> forceTemporaryDisconnect(counter.getContext(), mac));
            allow.setOnClickListener(v -> allowClient(counter.getContext(), mac));
            block.setOnClickListener(v -> blockClient(counter.getContext(), mac));
        }
    }

    private static void showWhitelistEditor(Context context, Object wifi) {
        try {
            Object cfg = invoke(wifi, "getSoftApConfiguration");
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(48, 8, 48, 8);

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            Runnable render = () -> renderMacList(context, list, allowed, "Разрешено", true);
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
                        applyClientLists(wifi, !allowed.isEmpty(), allowed,
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
            List<Object> allowed = copyList(invoke(cfg, "getAllowedClientList"));
            boolean control = readBoolean(cfg, "isClientControlByUserEnabled", false);

            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(28, 8, 28, 8);
            box.addView(rowSummary(context,
                    "Заблокированные устройства не смогут подключиться к точке доступа."));
            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            Runnable render = () -> renderBlockedList(
                    context, list, blocked, wifi, control, allowed);
            render.run();

            Button add = new Button(context);
            add.setText("Добавить MAC-адрес");
            box.addView(add);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Заблокированные устройства")
                    .setView(box)
                    .setNegativeButton("Закрыть", null)
                    .create();

            add.setOnClickListener(v -> askForMac(context, mac -> {
                if (!containsMac(blocked, mac)) {
                    blocked.add(parseMac(mac));
                    try {
                        applyClientLists(wifi, control, allowed, blocked);
                        render.run();
                    } catch (Throwable t) {
                        showError(context, "Блокировка", t);
                    }
                }
            }));
            dialog.show();
        } catch (Throwable t) {
            showError(context, "Блокировка", t);
        }
    }

    private static void renderBlockedList(Context context, LinearLayout list,
            List<Object> blocked, Object wifi, boolean control, List<Object> allowed) {
        list.removeAllViews();
        if (blocked.isEmpty()) {
            list.addView(rowSummary(context, "Список пуст"));
            return;
        }
        for (Object item : new ArrayList<>(blocked)) {
            String mac = macString(item);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 12, 0, 12);
            row.addView(rowTitle(context, deviceName(mac)));
            row.addView(rowSummary(context, mac));
            TextView unblock = smallAction(context, "Разблокировать");
            row.addView(unblock);
            unblock.setOnClickListener(v -> {
                try {
                    Object latest = invoke(wifi, "getSoftApConfiguration");
                    List<Object> latestBlocked = copyList(
                            invoke(latest, "getBlockedClientList"));
                    List<Object> latestAllowed = copyList(
                            invoke(latest, "getAllowedClientList"));
                    boolean latestControl = readBoolean(
                            latest, "isClientControlByUserEnabled", false);
                    latestBlocked.removeIf(x -> mac.equalsIgnoreCase(macString(x)));
                    if (latestControl && !containsMac(latestAllowed, mac)) {
                        latestAllowed.add(parseMac(mac));
                    }
                    applyClientLists(wifi, latestControl, latestAllowed, latestBlocked);
                    blocked.removeIf(x -> mac.equalsIgnoreCase(macString(x)));
                    allowed.clear();
                    allowed.addAll(latestAllowed);
                    renderBlockedList(context, list, blocked, wifi,
                            latestControl, allowed);
                    note("CLIENT_UNBLOCKED");
                } catch (Throwable t) {
                    showError(context, "Разблокировка", t);
                }
            });
            list.addView(row);
        }
    }

    private static void renderMacList(Context context, LinearLayout list,
            List<Object> macs, String prefix, boolean removable) {
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
            LinearLayout textBox = new LinearLayout(context);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.addView(rowTitle(context, deviceName(macString(mac))));
            textBox.addView(rowSummary(context, macString(mac)));
            row.addView(textBox, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = new Button(context);
            remove.setText("Заблокировано".equals(prefix) ? "Разблокировать" : "Удалить");
            remove.setOnClickListener(v -> {
                macs.remove(mac);
                renderMacList(context, list, macs, prefix, removable);
            });
            row.addView(remove);
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
            note("CLIENT_ALLOWED");
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
            boolean control = readBoolean(cfg, "isClientControlByUserEnabled", false);
            applyClientLists(wifi, control, allowed, blocked);
            note("CLIENT_BLOCKED");
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
            note("CLIENT_FORCE_DISCONNECT_REQUEST");

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Object latest = invoke(wifi, "getSoftApConfiguration");
                    List<Object> latestBlocked = copyList(invoke(latest, "getBlockedClientList"));
                    latestBlocked.removeIf(x -> mac.equalsIgnoreCase(macString(x)));
                    List<Object> latestAllowed = copyList(invoke(latest, "getAllowedClientList"));
                    boolean latestControl = readBoolean(
                            latest, "isClientControlByUserEnabled", false);
                    applyClientLists(wifi, latestControl, latestAllowed, latestBlocked);
                    note("CLIENT_FORCE_DISCONNECT_RELEASED");
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
            handle.counter = counter;
            handle.list = list;
            handle.maxEdit = maxEdit;
            Executor executor = command -> new Handler(Looper.getMainLooper()).post(command);

            Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                        if ("onConnectedClientsChanged".equals(method.getName()) && args != null) {
                            for (Object arg : args) {
                                if (arg instanceof List) {
                                    handle.clients = new ArrayList<>((List<Object>) arg);
                                    refreshClientsFromCallback(counter, list, maxEdit, handle.clients, handle);
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
            registerTetheringNameCallback(context, handle);
            note("CLIENT_CALLBACK_REGISTERED");
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
            unregisterTetheringNameCallback(handle);
            handle.callback = null;
            note("CLIENT_CALLBACK_UNREGISTERED");
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

    private static void registerTetheringNameCallback(Context context,
            ClientCallbackHandle handle) {
        try {
            Object manager = context.getSystemService("tethering");
            if (manager == null) return;
            Class<?> callbackClass = Class.forName(
                    "android.net.TetheringManager$TetheringEventCallback");
            Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                        if ("onClientsChanged".equals(method.getName())
                                && args != null && args.length > 0
                                && args[0] instanceof Iterable) {
                            for (Object client : (Iterable<?>) args[0]) {
                                try {
                                    String mac = macString(invoke(client, "getMacAddress"));
                                    Object addresses = invoke(client, "getAddresses");
                                    if (addresses instanceof Iterable) {
                                        for (Object address : (Iterable<?>) addresses) {
                                            Object value = invoke(address, "getHostname");
                                            if (value != null) {
                                                String hostname = String.valueOf(value).trim();
                                                if (!hostname.isEmpty()) {
                                                    CLIENT_NAMES.put(mac.toUpperCase(), hostname);
                                                    if (handle.counter != null && handle.list != null
                                                            && handle.maxEdit != null) {
                                                        refreshClientsFromCallback(handle.counter,
                                                                handle.list, handle.maxEdit,
                                                                handle.clients, handle);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) { }
                            }
                        }
                        return null;
                    });
            Method register = findMethod(manager.getClass(),
                    "registerTetheringEventCallback", 2);
            if (register == null) return;
            Executor executor = command -> new Handler(Looper.getMainLooper()).post(command);
            register.setAccessible(true);
            register.invoke(manager, executor, callback);
            handle.tetheringManager = manager;
            handle.tetheringCallback = callback;
            note("CLIENT_HOSTNAME_CALLBACK_REGISTERED");
        } catch (Throwable t) {
            note("CLIENT_HOSTNAME_CALLBACK_UNAVAILABLE "
                    + t.getClass().getSimpleName());
        }
    }

    private static void unregisterTetheringNameCallback(ClientCallbackHandle handle) {
        if (handle == null || handle.tetheringManager == null
                || handle.tetheringCallback == null) return;
        try {
            Method unregister = findMethod(handle.tetheringManager.getClass(),
                    "unregisterTetheringEventCallback", 1);
            if (unregister != null) {
                unregister.setAccessible(true);
                unregister.invoke(handle.tetheringManager, handle.tetheringCallback);
            }
        } catch (Throwable ignored) { }
        handle.tetheringManager = null;
        handle.tetheringCallback = null;
    }

    private static String deviceName(String mac) {
        if (mac != null) {
            String name = CLIENT_NAMES.get(mac.toUpperCase());
            if (name != null && !name.isEmpty()) return name;
        }
        return "Устройство";
    }

    private static TextView sectionLabel(Context context, String text) {
        TextView v = rowSummary(context, text);
        v.setTextSize(14);
        v.setPadding(4, 18, 4, 8);
        return v;
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 4, 20, 4);
        card.setBackground(roundBackground(0xFFFFFFFF, 22));
        return card;
    }

    private static TextView actionRow(Context context, String title, String summary) {
        TextView v = rowTitle(context, title);
        v.setText(title + "\n" + summary);
        v.setTextSize(16);
        v.setPadding(20, 16, 20, 16);
        v.setBackground(roundBackground(0xFFFFFFFF, 22));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private static TextView dialogButton(Context context, String text) {
        TextView v = smallAction(context, text);
        v.setPadding(18, 14, 18, 14);
        return v;
    }

    private static TextView smallAction(Context context, String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(0xFF1769E0);
        v.setGravity(Gravity.CENTER);
        v.setPadding(12, 10, 12, 10);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private static TextView rowTitle(Context context, String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(17);
        v.setTextColor(0xFF202124);
        return v;
    }

    private static TextView rowSummary(Context context, String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(0xFF777777);
        return v;
    }

    private static GradientDrawable roundBackground(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static LinearLayout valueRow(Context context, String title, String suffix, int value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 12, 0, 8);
        row.addView(rowSummary(context, title + " (" + suffix + ")"));
        EditText edit = new EditText(context);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(String.valueOf(value));
        edit.setTextSize(17);
        row.addView(edit);
        return row;
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
        Object tetheringManager;
        Object tetheringCallback;
        TextView counter;
        LinearLayout list;
        EditText maxEdit;
        List<Object> clients = new ArrayList<>();
    }
}
