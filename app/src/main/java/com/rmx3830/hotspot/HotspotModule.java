package com.rmx3830.hotspot;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Read-only diagnostic Modern Xposed module.
 * Never changes hotspot settings or logs SSID, MAC addresses or passphrases.
 */
public final class HotspotModule extends XposedModule {
    private static final String TAG = "RMX3830Hotspot";
    private static final int HOOKS_PER_CLASS = 8;
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
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!"com.android.settings".equals(param.getPackageName())) return;
        note("SETTINGS_READY package=" + param.getPackageName());
        probe(param.getClassLoader(), SETTINGS_CLASSES, false);
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
