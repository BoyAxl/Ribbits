package com.yungnickyoung.minecraft.yungsapi.api;

import com.yungnickyoung.minecraft.yungsapi.api.autoregister.AutoRegister;
import com.yungnickyoung.minecraft.yungsapi.autoregister.AutoRegistrationManager;
import com.yungnickyoung.minecraft.yungsapi.services.Services;

public class YungAutoRegister {
    /**
     * Scans for classes annotated with {@link AutoRegister}. Should be invoked at the beginning of mod initialization.
     * <br />
     * <p>
     * Any <b>fields</b> annotated with {@link AutoRegister} in these classes will be automatically registered.
     * </p>
     * <p>
     * Any <b>methods</b> annotated with {@link AutoRegister} in these classes will be queued for execution after registration.
     * <b>Note that these methods must be static and have no parameters.</b>
     * </p>
     *
     * @param packageName Name of a package containing {@link AutoRegister} annotated fields.
     *                    When specifying a package, try to be as precise as possible,
     *                    as all subpackages will also be recursively scanned, which can be a costly operation.
     */
    public static void scanPackageForAnnotations(String packageName) {
        if (Services.PLATFORM.getPlatformName().equals("Fabric")) {
            AutoRegistrationManager.initAutoRegPackage(packageName);
        }
    }
}
