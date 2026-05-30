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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;

public class BlacklistUtils {

    private static final int MAX_CONTAINER_DEPTH = 3;

    // cached at reload — avoids reading config on every item check
    private static boolean enabled = true;
    private static boolean exemptIfHasLore = false;
    private static boolean checkContainers = true;
    private static List<NamespacedKey> pdcExemptKeys = new ArrayList<>();
    private static List<Map<String, Object>> blacklistItems = new ArrayList<>();
    private static Set<Material> legacyMaterials = EnumSet.noneOf(Material.class);

    public static void reload() {
        enabled = CONFIG.getBoolean("blacklist-enabled", true);
        exemptIfHasLore = CONFIG.getBoolean("blacklist-exempt-if-has-lore", false);
        checkContainers = CONFIG.getBoolean("blacklist-check-containers", true);

        // parse PDC exempt keys once
        pdcExemptKeys = new ArrayList<>();
        List<String> rawKeys = CONFIG.getStringList("blacklist-pdc-exempt");
        if (rawKeys != null) {
            for (String raw : rawKeys) {
                String[] parts = raw.split(":", 2);
                if (parts.length == 2) pdcExemptKeys.add(new NamespacedKey(parts[0], parts[1]));
            }
        }

        // cache new blacklist-items list
        List<Map<String, Object>> items = CONFIG.getMapList("blacklist-items");
        blacklistItems = items != null ? items : new ArrayList<>();

        // cache legacy blacklisted-items section as a fast Set<Material>
        legacyMaterials = EnumSet.noneOf(Material.class);
        Section section = CONFIG.getSection("blacklisted-items");
        if (section != null) {
            for (String key : section.getRoutesAsStrings(false)) {
                String matName = CONFIG.getString("blacklisted-items." + key + ".material");
                if (matName == null) continue;
                try {
                    Material m = Material.valueOf(matName.toUpperCase());
                    legacyMaterials.add(m);
                } catch (IllegalArgumentException ignored) {
                    // unknown material name in config — skip silently
                }
            }
        }
    }

    public static boolean isBlacklisted(@Nullable ItemStack it) {
        return isBlacklisted(it, 0);
    }

    static boolean isBlacklisted(@Nullable ItemStack it, int depth) {
        if (!enabled) return false;
        if (it == null || it.getType() == Material.AIR) return false;
        if (depth > MAX_CONTAINER_DEPTH) return false;

        boolean exempt = isExemptByPdc(it) || isExemptByLore(it);

        if (!exempt) {
            if (legacyMaterials.contains(it.getType())) return true;
            if (!blacklistItems.isEmpty()) {
                WrappedItemStack wrap = WrappedItemStack.wrap(it);
                for (Map<String, Object> map : blacklistItems) {
                    if (new ItemMatcher(wrap, map).isMatching()) return true;
                }
            }
        }

        if (checkContainers && containsBlacklisted(it, depth)) return true;

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
        if (!exemptIfHasLore) return false;
        if (it.getItemMeta() == null) return false;
        List<String> lore = it.getItemMeta().getLore();
        return lore != null && !lore.isEmpty();
    }

    private static boolean isExemptByPdc(ItemStack it) {
        if (pdcExemptKeys.isEmpty()) return false;
        if (it.getItemMeta() == null) return false;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdcExemptKeys) {
            if (pdc.has(key, PersistentDataType.STRING)) return true;
        }
        return false;
    }
}
