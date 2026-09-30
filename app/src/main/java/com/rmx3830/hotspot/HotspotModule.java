package com.rmx3830.hotspot;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Modern Xposed hotspot extension module.
 * Only changes hotspot settings when the user explicitly applies values in the injected Settings editor; never logs SSID, MAC addresses or passphrases.
 */
public final class HotspotModule extends XposedModule {
    private static final String TAG = "RMX3830Hotspot";
    private static final int HOOKS_PER_CLASS = 8;
    private static final String WIFI_APEX_JAR = "/apex/com.android.wifi/javalib/service-wifi.jar";
    private static final String[] FRAMEWORK_CLASSES = {
        "android.net.wifi.SoftApConfiguration",
        "android.net.wifi.SoftApConfiguration$Builder",
        "android.net.wifi.SoftApCapability",
        "android.net.wifi.SoftApInfo",
        "com.android.server.wifi.SoftApManager",
        "com.android.server.wifi.WifiServiceImpl"
    };
    private static final String[] SETTINGS_CLASSES = {
        "com.android.settings.wifi.tether.WifiTetherSettings",
        "com.android.settings.TetherSettings",
        "com.android.settings.network.TetherSettings"
    };
    private static final Set<String> OBSERVER_HOOKS = new HashSet<>();
    private static boolean classLoaderHookInstalled;
    private static boolean wifiJarHookInstalled;
    private static boolean settingsUiHookInstalled;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        note("MODULE_LOADED api=" + getApiVersion()
            + " sdk=" + Build.VERSION.SDK_INT
            + " process=" + param.getProcessName()
            + " systemServer=" + param.isSystemServer());
    }

    @Override
    public void onSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        note("SYSTEM_SERVER_START classLoader=" + param.getClassLoader());
        probe(param.getClassLoader(), FRAMEWORK_CLASSES, true);
        installWifiClassLoaderProbe();
        installWifiJarProbe(param.getClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!"com.android.settings".equals(param.getPackageName())) return;
        note("SETTINGS_READY package=" + param.getPackageName());
        probe(param.getClassLoader(), SETTINGS_CLASSES, false);
        installSettingsUiHook(param.getClassLoader());
    }

    private void installSettingsUiHook(ClassLoader settingsLoader) {
        if (settingsUiHookInstalled) return;
        try {
            Class<?> settingsClass = Class.forName(
                "com.android.settings.wifi.tether.WifiTetherSettings", false, settingsLoader);
            Method target = null;
            for (Method method : settingsClass.getDeclaredMethods()) {
                if (!"onCreate".equals(method.getName())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 1 && types[0] == android.os.Bundle.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                note("SETTINGS_UI_HOOK_FAILED onCreate_missing");
                return;
            }
            hook(target).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    AdvancedHotspotUi.inject(chain.getThisObject());
                } catch (Throwable t) {
                    note("SETTINGS_UI_INJECT_FAILED " + t.getClass().getSimpleName());
                }
                return result;
            });
            settingsUiHookInstalled = true;
            note("SETTINGS_UI_HOOK_READY");
        } catch (Throwable t) {
            note("SETTINGS_UI_HOOK_FAILED " + t.getClass().getSimpleName());
        }
    }

    private void installWifiClassLoaderProbe() {
        if (classLoaderHookInstalled) return;
        try {
            Method loadClass = ClassLoader.class.getDeclaredMethod("loadClass", String.class);
            hook(loadClass).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object firstArg = chain.getArgs().get(0);
                    String name = firstArg instanceof String ? (String) firstArg : null;
                    if (name != null && isWifiTarget(name) && result instanceof Class) {
                        Class<?> cls = (Class<?>) result;
                        note("CLASS_LOADED_BY " + cls.getName()
                            + " loader=" + cls.getClassLoader());
                        probeLoadedClass(cls);
                    }
                } catch (Throwable ignored) { }
                return result;
            });
            classLoaderHookInstalled = true;
            note("CLASSLOADER_HOOK_READY");
        } catch (Throwable t) {
            note("CLASSLOADER_HOOK_FAILED " + t.getClass().getSimpleName());
        }
    }

    private void installWifiJarProbe(ClassLoader systemServerLoader) {
        if (wifiJarHookInstalled) return;
        try {
            Class<?> managerClass = Class.forName(
                "com.android.server.SystemServiceManager", false, systemServerLoader);
            Method target = null;
            for (Method method : managerClass.getDeclaredMethods()) {
                if (!"startServiceFromJar".equals(method.getName())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2
                        && types[0] == String.class
                        && types[1] == String.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                note("WIFI_JAR_HOOK_FAILED method_missing");
                return;
            }
            hook(target).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                Object result = chain.proceed();
                try {
                    String path = args.length > 1 && args[1] instanceof String
                        ? (String) args[1] : "";
                    String className = args.length > 0 && args[0] instanceof String
                        ? (String) args[0] : "";
                    if (WIFI_APEX_JAR.equals(path) || path.contains("/com.android.wifi/")) {
                        note("WIFI_JAR_STARTED class=" + className + " path=" + path);
                        probeWifiServiceManager(chain.getThisObject());
                    }
                } catch (Throwable t) {
                    note("WIFI_JAR_SCAN_FAILED " + t.getClass().getSimpleName());
                }
                return result;
            });
            wifiJarHookInstalled = true;
            note("WIFI_JAR_HOOK_READY");
        } catch (Throwable t) {
            note("WIFI_JAR_HOOK_FAILED " + t.getClass().getSimpleName());
        }
    }

    private void probeWifiServiceManager(Object manager) {
        if (manager == null) return;
        try {
            Field servicesField = manager.getClass().getDeclaredField("mServices");
            servicesField.setAccessible(true);
            Object services = servicesField.get(manager);
            if (!(services instanceof Iterable)) {
                note("WIFI_SERVICES_FIELD_UNUSABLE");
                return;
            }
            for (Object service : (Iterable<?>) services) {
                if (service == null) continue;
                Class<?> serviceClass = service.getClass();
                String name = serviceClass.getName();
                if (!name.startsWith("com.android.server.wifi.")) continue;
                ClassLoader wifiLoader = serviceClass.getClassLoader();
                note("WIFI_SERVICE_FOUND " + name + " loader=" + wifiLoader);
                probeWifiClassLoader(wifiLoader);
            }
        } catch (Throwable t) {
            note("WIFI_SERVICE_SCAN_FAILED " + t.getClass().getSimpleName());
        }
    }

    private void probeWifiClassLoader(ClassLoader loader) {
        if (loader == null) return;
        for (String className : FRAMEWORK_CLASSES) {
            if (!className.startsWith("com.android.server.wifi.")) continue;
            try {
                Class<?> cls = Class.forName(className, false, loader);
                note("WIFI_CLASS_FOUND " + className + " loader=" + loader);
                probeLoadedClass(cls);
            } catch (Throwable t) {
                note("WIFI_CLASS_MISSING " + className
                    + " " + t.getClass().getSimpleName());
            }
        }
    }

    private static boolean isWifiTarget(String name) {
        return "com.android.server.wifi.SoftApManager".equals(name)
            || "com.android.server.wifi.WifiServiceImpl".equals(name)
            || name.startsWith("com.android.server.wifi.");
    }

    private void probeLoadedClass(Class<?> cls) {
        int hooked = 0;
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!interesting(name)) continue;
            note("WIFI_METHOD " + method.toGenericString());
            if (hooked >= HOOKS_PER_CLASS || !safeHookName(name)
                    || Modifier.isAbstract(method.getModifiers())
                    || Modifier.isNative(method.getModifiers())) continue;
            try {
                String key = cls.getName() + "#" + method.toGenericString();
                if (OBSERVER_HOOKS.add(key)) {
                    final String signature = cls.getSimpleName() + "." + method.getName();
                    hook(method).intercept(chain -> {
                        note("OBSERVED " + signature);
                        return chain.proceed();
                    });
                    hooked++;
                }
            } catch (Throwable hookError) {
                note("HOOK_SKIPPED " + method.getName() + " "
                    + hookError.getClass().getSimpleName());
            }
        }
        note("WIFI_CLASS_PROBED " + cls.getName() + " hooks=" + hooked);
    }

    private void probe(ClassLoader loader, String[] targets, boolean allowHooks) {
        for (String className : targets) {
            try {
                Class<?> cls = Class.forName(className, false, loader);
                note("CLASS_FOUND " + className);
                int hooked = 0;
                for (Method method : cls.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    if (!interesting(name)) continue;
                    note("METHOD " + method.toGenericString());
                    if (!allowHooks || hooked >= HOOKS_PER_CLASS
                            || !safeHookName(name)
                            || Modifier.isAbstract(method.getModifiers())
                            || Modifier.isNative(method.getModifiers())) continue;
                    try {
                        final String signature = method.getDeclaringClass().getSimpleName()
                            + "." + method.getName();
                        hook(method).intercept(chain -> {
                            note("OBSERVED " + signature);
                            return chain.proceed();
                        });
                        hooked++;
                    } catch (Throwable hookError) {
                        note("HOOK_SKIPPED " + method.getName() + " "
                            + hookError.getClass().getSimpleName());
                    }
                }
                note("CLASS_PROBED " + className + " hooks=" + hooked);
            } catch (Throwable probeError) {
                note("CLASS_MISSING " + className + " "
                    + probeError.getClass().getSimpleName());
            }
        }
    }

    private static boolean interesting(String s) {
        return s.contains("softap") || s.contains("client")
            || s.contains("channel") || s.contains("band")
            || s.contains("hidden") || s.contains("shutdown")
            || s.contains("ssid") || s.contains("passphrase")
            || s.contains("isolat") || s.contains("allowed")
            || s.contains("blocked") || s.contains("timeout")
            || s.contains("connected") || s.contains("capabil");
    }

    private static boolean safeHookName(String s) {
        return s.equals("getmaxnumberofclients") || s.equals("getshutdown")
            || s.equals("isautoshutdownenabled") || s.equals("ishiddenssid")
            || s.equals("getbands") || s.equals("getchannel")
            || s.equals("getchannels") || s.equals("getmaxsupportedclients")
            || s.equals("getconnectedclientlist") || s.equals("getcurrentsoftapconfiguration")
            || s.equals("getcurrentsoftapcapability") || s.equals("getsoftapconfiguration");
    }

    private static void note(String message) {
        try { Log.i(TAG, message); } catch (Throwable ignored) { }
    }
}
