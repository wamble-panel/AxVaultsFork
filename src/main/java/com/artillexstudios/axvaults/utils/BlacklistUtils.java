package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;

public class BlacklistUtils {

    private static final int MAX_CONTAINER_DEPTH = 3;

    public static boolean isBlacklisted(@Nullable ItemStack it) {
        return isBlacklisted(it, 0);
    }

    private static boolean isBlacklisted(@Nullable ItemStack it, int depth) {
        if (it == null || it.getType() == Material.AIR) return false;
        if (depth > MAX_CONTAINER_DEPTH) return false;

        boolean exempt = isExemptByPdc(it) || isExemptByLore(it);

        if (!exempt) {
            if (checkLegacy(it)) return true;
            try {
                List<Map<String, Object>> list = CONFIG.getMapList("blacklist-items");
                if (list != null && !list.isEmpty()) {
                    WrappedItemStack wrap = WrappedItemStack.wrap(it);
                    for (Map<String, Object> map : list) {
                        ItemMatcher matcher = new ItemMatcher(wrap, map);
                        if (matcher.isMatching()) return true;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Check container contents regardless of exemption — prevents using an
        // exempt wrapper (e.g. a "voucher" shulker) to smuggle blacklisted items in.
        if (CONFIG.getBoolean("blacklist-check-containers", true) && containsBlacklisted(it, depth)) {
            return true;
        }

        return false;
    }

    private static boolean containsBlacklisted(ItemStack it, int depth) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
            BlockState state = bsm.getBlockState();
            if (state instanceof ShulkerBox shulker) {
                for (ItemStack content : shulker.getInventory().getContents()) {
                    if (isBlacklisted(content, depth + 1)) return true;
                }
            }
        }

        if (meta instanceof BundleMeta bm) {
            for (ItemStack content : bm.getItems()) {
                if (isBlacklisted(content, depth + 1)) return true;
            }
        }

        return false;
    }

    private static boolean isExemptByLore(ItemStack it) {
        if (!CONFIG.getBoolean("blacklist-exempt-if-has-lore", false)) return false;
        if (it.getItemMeta() == null) return false;
        List<String> lore = it.getItemMeta().getLore();
        return lore != null && !lore.isEmpty();
    }

    private static boolean isExemptByPdc(ItemStack it) {
        if (it.getItemMeta() == null) return false;
        List<String> exemptKeys = CONFIG.getStringList("blacklist-pdc-exempt");
        if (exemptKeys == null || exemptKeys.isEmpty()) return false;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        for (String raw : exemptKeys) {
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) continue;
            NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
            if (pdc.has(key, PersistentDataType.STRING)) return true;
        }
        return false;
    }

    private static boolean checkLegacy(ItemStack it) {
        final Section section = CONFIG.getSection("blacklisted-items");
        if (section == null) return false;
        for (String s : section.getRoutesAsStrings(false)) {
            if (CONFIG.getString("blacklisted-items." + s + ".material") != null) {
                if (!it.getType().toString().equalsIgnoreCase(CONFIG.getString("blacklisted-items." + s + ".material"))) continue;
                return true;
            }

            if (CONFIG.getString("blacklisted-items." + s + ".name-contains") != null) {
                if (it.getItemMeta() == null) continue;
                if (!it.getItemMeta().getDisplayName().contains(CONFIG.getString("blacklisted-items." + s + ".name-contains"))) continue;
                return true;
            }
        }
        return false;
    }
}
