package com.artillexstudios.axvaults.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

public class SerializationUtils {

    @Nullable
    public static ItemStack[] invFromBits(InputStream stream) {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(stream)) {
            return (ItemStack[]) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
