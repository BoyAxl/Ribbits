package com.yungnickyoung.minecraft.yungsapi.api.autoregister;

import com.yungnickyoung.minecraft.yungsapi.autoregister.AutoRegisterEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper for registering {@link CreativeModeTab}s with AutoRegister.
 */
public class AutoRegisterCreativeTab extends AutoRegisterEntry<CreativeModeTab> {
    private final Component displayName;
    private final Supplier<ItemStack> iconGenerator;
    private final List<Supplier<? extends ItemLike>> entries;
    private final boolean canScroll;
    private final boolean showTitle;
    private final boolean alignedRight;
    private final Identifier backgroundTexture;

    private AutoRegisterCreativeTab(Builder builder) {
        super(() -> null);
        this.displayName = builder.displayName;
        this.iconGenerator = builder.iconGenerator;
        this.entries = List.copyOf(builder.entries);
        this.canScroll = builder.canScroll;
        this.showTitle = builder.showTitle;
        this.alignedRight = builder.alignedRight;
        this.backgroundTexture = builder.backgroundTexture;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Component getDisplayName() {
        return displayName;
    }

    public Supplier<ItemStack> getIconItemStackSupplier() {
        return iconGenerator;
    }

    public List<Supplier<? extends ItemLike>> getEntries() {
        return entries;
    }

    public boolean canScroll() {
        return canScroll;
    }

    public boolean showTitle() {
        return showTitle;
    }

    public boolean alignedRight() {
        return alignedRight;
    }

    public Identifier getBackgroundTexture() {
        return backgroundTexture;
    }

    public static class Builder {
        private Component displayName = Component.empty();
        private Supplier<ItemStack> iconGenerator = () -> ItemStack.EMPTY;
        private List<Supplier<? extends ItemLike>> entries = List.of();
        private boolean canScroll = true;
        private boolean showTitle = true;
        private boolean alignedRight = false;
        private Identifier backgroundTexture = null;

        private Builder() {
        }

        public Builder iconItem(Supplier<ItemStack> iconItemStack) {
            this.iconGenerator = iconItemStack;
            return this;
        }

        public Builder title(Component title) {
            this.displayName = title;
            return this;
        }

        @SafeVarargs
        public final Builder entries(Supplier<? extends ItemLike>... entries) {
            this.entries = Arrays.asList(entries);
            return this;
        }

        public Builder alignedRight() {
            this.alignedRight = true;
            return this;
        }

        public Builder hideTitle() {
            this.showTitle = false;
            return this;
        }

        public Builder noScrollBar() {
            this.canScroll = false;
            return this;
        }

        public Builder backgroundTexture(Identifier backgroundTexture) {
            this.backgroundTexture = backgroundTexture;
            return this;
        }

        public AutoRegisterCreativeTab build() {
            return new AutoRegisterCreativeTab(this);
        }
    }
}
