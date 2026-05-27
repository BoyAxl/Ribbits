package com.yungnickyoung.minecraft.ribbits.module;

import com.yungnickyoung.minecraft.ribbits.RibbitsCommon;
import com.yungnickyoung.minecraft.yungsapi.api.autoregister.AutoRegister;
import com.yungnickyoung.minecraft.yungsapi.api.autoregister.AutoRegisterCreativeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@AutoRegister(RibbitsCommon.MOD_ID)
public class CreativeTabModule {
    @AutoRegister("general")
    public static AutoRegisterCreativeTab TAB = AutoRegisterCreativeTab.builder()
            .title(Component.translatable("itemGroup.ribbits.general"))
            .iconItem(() -> new ItemStack(BlockModule.RED_TOADSTOOL.get()))
            .entries(
                    BlockModule.RED_TOADSTOOL::get,
                    BlockModule.BROWN_TOADSTOOL::get,
                    BlockModule.TOADSTOOL_STEM::get,
                    BlockModule.SWAMP_LANTERN::get,
                    ItemModule.GIANT_LILYPAD::get,
                    BlockModule.SWAMP_DAISY::get,
                    BlockModule.TOADSTOOL::get,
                    BlockModule.UMBRELLA_LEAF::get,
                    BlockModule.MOSSY_OAK_PLANKS::get,
                    BlockModule.MOSSY_OAK_PLANKS::getStairs,
                    BlockModule.MOSSY_OAK_PLANKS::getSlab,
                    BlockModule.MOSSY_OAK_PLANKS::getFence,
                    BlockModule.MOSSY_OAK_PLANKS::getFenceGate,
                    BlockModule.MOSSY_OAK_DOOR::get,
                    ItemModule.MARACA::get,
                    ItemModule.RIBBIT_NITWIT_SPAWN_EGG::get,
                    ItemModule.RIBBIT_FISHERMAN_SPAWN_EGG::get,
                    ItemModule.RIBBIT_GARDENER_SPAWN_EGG::get,
                    ItemModule.RIBBIT_MERCHANT_SPAWN_EGG::get,
                    ItemModule.RIBBIT_SORCERER_SPAWN_EGG::get
            )
            .build();
}
