package com.yungnickyoung.minecraft.yungsapi.module;

import com.yungnickyoung.minecraft.yungsapi.api.autoregister.AutoRegisterCreativeTab;
import com.yungnickyoung.minecraft.yungsapi.autoregister.AutoRegisterField;
import com.yungnickyoung.minecraft.yungsapi.autoregister.AutoRegistrationManager;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Initialization of creative mode tabs.
 */
public class CreativeModeTabModuleFabric {
    public static void processEntries() {
        AutoRegistrationManager.CREATIVE_MODE_TABS.stream()
                .filter(data -> !data.processed())
                .forEach(CreativeModeTabModuleFabric::initialize);
    }

    private static void initialize(AutoRegisterField data) {
        // Extract data
        AutoRegisterCreativeTab autoRegisterCreativeTab = (AutoRegisterCreativeTab) data.object();

        // Create tab
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, data.name());
        CreativeModeTab.Builder creativeModeTabBuilder = FabricCreativeModeTab.builder()
                .title(autoRegisterCreativeTab.getDisplayName())
                .icon(autoRegisterCreativeTab.getIconItemStackSupplier());
        if (autoRegisterCreativeTab.getBackgroundTexture() != null) {
            creativeModeTabBuilder.backgroundTexture(autoRegisterCreativeTab.getBackgroundTexture());
        }
        if (!autoRegisterCreativeTab.canScroll()) {
            creativeModeTabBuilder.noScrollBar();
        }
        if (!autoRegisterCreativeTab.showTitle()) {
            creativeModeTabBuilder.hideTitle();
        }
        if (autoRegisterCreativeTab.alignedRight()) {
            creativeModeTabBuilder.alignedRight();
        }

        CreativeModeTab creativeModeTab = creativeModeTabBuilder.build();

        // Register tab
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, data.name(), creativeModeTab);
        CreativeModeTabEvents.modifyOutputEvent(key).register(output ->
                autoRegisterCreativeTab.getEntries().forEach(entry -> output.accept(entry.get())));

        // Update supplier to retrieve tab
        autoRegisterCreativeTab.setSupplier(() -> creativeModeTab);

        data.markProcessed();
    }
}
