package com.rmx3830.hotspot;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Read-only diagnostic Modern Xposed module.
 * Never changes hotspot settings or logs SSID, MAC addresses or passphrases.
 */
public final class HotspotModule extends XposedModule {
    private static final String TAG = "RMX3830Hotspot";
    private static final int HOOKS_PER_CLASS = 4;
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
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!"com.android.settings".equals(param.getPackageName())) return;
        note("SETTINGS_READY package=" + param.getPackageName());
        probe(param.getClassLoader(), SETTINGS_CLASSES, false);
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
                            // Never log arguments or return values.
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
            || s.contains("blocked") || s.contains("timeout");
    }

    // Passive getter hooks only. Never hook setters/start/stop or Binder entry points.
    private static boolean safeHookName(String s) {
        return s.equals("getmaxnumberofclients") || s.equals("getshutdown")
            || s.equals("isautoshutdownenabled") || s.equals("ishiddenssid")
            || s.equals("getbands") || s.equals("getchannel")
            || s.equals("getchannels") || s.equals("getmaxsupportedclients");
    }

    private static void note(String message) {
        try { Log.i(TAG, message); } catch (Throwable ignored) { }
    }
}
