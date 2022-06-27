package com.oheers.fish;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class NbtUtils {
    public static class Keys {
        public static final String EMF_COMPOUND = JavaPlugin.getProvidingPlugin(NbtUtils.class).getName().toLowerCase(Locale.ROOT);
        public static final String EMF_FISH_PLAYER = "emf-fish-player";
        public static final String EMF_FISH_RARITY = "emf-fish-rarity";
        public static final String EMF_FISH_LENGTH = "emf-fish-length";
        public static final String EMF_FISH_NAME = "emf-fish-name";
        public static final String EMF_BAIT = "emf-bait";

        public static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";
        public static final String DEFAULT_GUI_ITEM = "default-gui-item";
    }

    /*
        3 possible options:
        legacy: <-- check for this first
            compound for PublicBukkitValues
        nbt-api-pr: <-- then this
            evenmorefish:key: value
        nbt-compat-pr: <-- then this
            compound for evenmorefish:
                            key: value
     */
    public static boolean hasKey(final @NotNull NBTCompound nbtCompound, final String key) {
        NamespacedKey namespacedKey = getNamespacedKey(key);

        //Pre NBT-API PR
        if(Boolean.TRUE.equals(nbtCompound.hasKey(Keys.PUBLIC_BUKKIT_VALUES))) {
            NBTCompound publicBukkitValues = nbtCompound.getCompound(Keys.PUBLIC_BUKKIT_VALUES);
            if(Boolean.TRUE.equals(publicBukkitValues.hasKey(namespacedKey.toString())))
                return true;
        }

        //NBT API PR
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.toString())))
            return true;

        //NBT COMPAT
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.getNamespace()))) {
            NBTCompound emfCompound = nbtCompound.getCompound(namespacedKey.getNamespace());
            return Boolean.TRUE.equals(emfCompound.hasKey(namespacedKey.getKey()));
        }

        return false;
    }


    public static @Nullable String getString(final @NotNull NBTCompound nbtCompound, final String key) {
        NamespacedKey namespacedKey = getNamespacedKey(key);
        if(Boolean.TRUE.equals(nbtCompound.hasKey(Keys.PUBLIC_BUKKIT_VALUES))) {
            NBTCompound publicBukkitValues = nbtCompound.getCompound(Keys.PUBLIC_BUKKIT_VALUES);
            if(Boolean.TRUE.equals(publicBukkitValues.hasKey(namespacedKey.toString())))
                return publicBukkitValues.getString(namespacedKey.toString());
        }

        //NBT API PR
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.toString())))
            return nbtCompound.getString(namespacedKey.toString());

        //NBT COMPAT
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.getNamespace()))) {
            NBTCompound emfCompound = nbtCompound.getCompound(namespacedKey.getNamespace());
            if(Boolean.TRUE.equals(emfCompound.hasKey(namespacedKey.getKey())))
                return nbtCompound.getString(namespacedKey.getKey());
        }

        return null;
    }

    public static @Nullable Float getFloat(final @NotNull NBTCompound nbtCompound, final String key) {
        NamespacedKey namespacedKey = getNamespacedKey(key);
        if(Boolean.TRUE.equals(nbtCompound.hasKey(Keys.PUBLIC_BUKKIT_VALUES))) {
            NBTCompound publicBukkitValues = nbtCompound.getCompound(Keys.PUBLIC_BUKKIT_VALUES);
            if(Boolean.TRUE.equals(publicBukkitValues.hasKey(namespacedKey.toString())))
                return publicBukkitValues.getFloat(namespacedKey.toString());
        }

        //NBT API PR
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.toString())))
            return nbtCompound.getFloat(namespacedKey.toString());

        //NBT COMPAT
        if(Boolean.TRUE.equals(nbtCompound.hasKey(namespacedKey.getNamespace()))) {
            NBTCompound emfCompound = nbtCompound.getCompound(namespacedKey.getNamespace());
            if(Boolean.TRUE.equals(emfCompound.hasKey(namespacedKey.getKey())))
                return nbtCompound.getFloat(namespacedKey.getKey());
        }

        return null;
    }
    //todo could be void?
    public static @NotNull NBTCompound setByte(final @NotNull NBTCompound nbtCompound, final String key, final Byte value) {
        final NBTCompound emfCompound = nbtCompound.getOrCreateCompound(Keys.EMF_COMPOUND);
        emfCompound.setByte(key,value);
        return emfCompound;
    }

    public static @NotNull NBTCompound setString(final @NotNull NBTCompound nbtCompound, final String key, final String value) {
        final NBTCompound emfCompound = nbtCompound.getOrCreateCompound(Keys.EMF_COMPOUND);
        emfCompound.setString(key,value);
        return emfCompound;
    }

    public static @NotNull NBTCompound setFloat(final @NotNull NBTCompound nbtCompound, final String key, final Float value) {
        final NBTCompound emfCompound = nbtCompound.getOrCreateCompound(Keys.EMF_COMPOUND);
        emfCompound.setFloat(key,value);
        return emfCompound;
    }

    /**
     * Returns the NBT Version of the item
     * It does not mean that this is an emf item.
     * @param compound
     * @return
     */
    public static NbtVersion getNbtVersion(final NBTCompound compound) {
        if(Boolean.TRUE.equals(compound.hasKey(Keys.EMF_COMPOUND)))
            return NbtVersion.COMPAT; //def an emf item
        if(Boolean.TRUE.equals(compound.hasKey(Keys.PUBLIC_BUKKIT_VALUES)))
            return NbtVersion.LEGACY;
        return NbtVersion.NBTAPI;
    }


    @Contract("_ -> new")
    private static @NotNull NamespacedKey getNamespacedKey(final String key) {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(NbtUtils.class), key);
    }

    public enum NbtVersion {
        LEGACY, //pre nbt-api pr
        NBTAPI, //nbt-api pr
        COMPAT //compatible with everything :)
    }
}
