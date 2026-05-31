package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.items.WrappedItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

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
     * The item bytes are tried with several strategies so the vault loads
     * rather than failing entirely. Unrecoverable individual items become AIR.
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
    private static ItemStack tryDeserializeItem(byte[] itemBytes) {
        // Try 1: GZIP-wrap the raw bytes so WrappedItemStack (which expects GZIP) can read them.
        // This handles the intermediate axapi format where items were stored as uncompressed NBT.
        try {
            return WrappedItemStack.wrap(gzip(itemBytes)).toBukkit();
        } catch (Exception ignored) {}

        // Try 2: base64 decode → GZIP-wrap (for base64-encoded uncompressed NBT)
        try {
            byte[] decoded = Base64.getDecoder().decode(itemBytes);
            return WrappedItemStack.wrap(gzip(decoded)).toBukkit();
        } catch (Exception ignored) {}

        // Try 3: base64 decode, already GZIP (for base64-encoded GZIP NBT)
        try {
            byte[] decoded = Base64.getDecoder().decode(itemBytes);
            return WrappedItemStack.wrap(decoded).toBukkit();
        } catch (Exception ignored) {}

        return null;
    }

    @Nullable
    private static byte[] gzip(byte[] bytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(bytes);
            gzos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
