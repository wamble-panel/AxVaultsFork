package com.artillexstudios.axvaults.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public class SerializationUtils {

    @Nullable
    public static ItemStack[] invFromBits(InputStream stream) {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(stream)) {
            return (ItemStack[]) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback for the intermediate axapi format: outer array is
     * (4-byte int count) + per-item (2-byte unsigned short size + size bytes).
     * Each item's bytes are tried as GZIP-compressed NBT, then as raw NBT,
     * then as Base64-encoded variants of those. Items that cannot be recovered
     * become AIR — the vault loads rather than failing entirely.
     */
    @Nullable
    public static ItemStack[] tryManualDeserialize(byte[] bytes) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int length = dis.readInt();
            if (length < 0 || length > 10_000) return null;

            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = dis.readShort() & 0xFFFF;
                if (size == 0) {
                    items[i] = new ItemStack(Material.AIR);
                    continue;
                }
                byte[] itemBytes = new byte[size];
                dis.readFully(itemBytes);
                ItemStack recovered = tryDeserializeItem(itemBytes);
                items[i] = recovered != null ? recovered : new ItemStack(Material.AIR);
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static ItemStack tryDeserializeItem(byte[] itemBytes) {
        // Try 1: raw bytes directly (expected by Bukkit.getUnsafe().deserializeItem)
        try {
            return Bukkit.getUnsafe().deserializeItem(itemBytes);
        } catch (Exception ignored) {}

        // Try 2: GZIP-decompress then deserialize
        try {
            byte[] decompressed = gunzip(itemBytes);
            if (decompressed != null) {
                return Bukkit.getUnsafe().deserializeItem(decompressed);
            }
        } catch (Exception ignored) {}

        // Try 3: treat bytes as Base64 text → raw
        try {
            byte[] decoded = Base64.getDecoder().decode(itemBytes);
            return Bukkit.getUnsafe().deserializeItem(decoded);
        } catch (Exception ignored) {}

        // Try 4: Base64 text → GZIP decompress
        try {
            byte[] decoded = Base64.getDecoder().decode(itemBytes);
            byte[] decompressed = gunzip(decoded);
            if (decompressed != null) {
                return Bukkit.getUnsafe().deserializeItem(decompressed);
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Nullable
    private static byte[] gunzip(byte[] bytes) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gz.transferTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
