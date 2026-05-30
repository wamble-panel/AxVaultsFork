package com.artillexstudios.axvaults.vaults;

import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axvaults.utils.BlacklistUtils;
import com.artillexstudios.axvaults.utils.ItemMatcher;
import com.artillexstudios.axvaults.utils.SimpleRegex;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;

public class VaultRules {

    // cached per-vault override configs — rebuilt on reload
    private static final Map<Integer, VaultRules> cache = new HashMap<>();

    private final boolean hasOverride;
    private final boolean bypassGlobal;
    private final List<Map<String, Object>> vaultBlacklist;
    private final List<Map<String, Object>> itemLimits;

    private VaultRules(int vaultId) {
        hasOverride = CONFIG.getSection("vault-overrides." + vaultId) != null;
        bypassGlobal = hasOverride && CONFIG.getBoolean("vault-overrides." + vaultId + ".bypass-global-blacklist", false);
        List<Map<String, Object>> bl = hasOverride ? CONFIG.getMapList("vault-overrides." + vaultId + ".blacklist-items") : null;
        vaultBlacklist = bl != null ? bl : Collections.emptyList();
        List<Map<String, Object>> lim = hasOverride ? CONFIG.getMapList("vault-overrides." + vaultId + ".item-limits") : null;
        itemLimits = lim != null ? lim : Collections.emptyList();
    }

    public static VaultRules of(int vaultId) {
        return cache.computeIfAbsent(vaultId, VaultRules::new);
    }

    /** Call on plugin reload to flush cached override data. */
    public static void invalidateCache() {
        cache.clear();
    }

    public boolean hasOverride() {
        return hasOverride;
    }

    public boolean isBlacklisted(@Nullable ItemStack item) {
        if (!hasOverride) return BlacklistUtils.isBlacklisted(item);
        if (bypassGlobal) return isVaultBlacklisted(item);
        return BlacklistUtils.isBlacklisted(item) || isVaultBlacklisted(item);
    }

    private boolean isVaultBlacklisted(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (vaultBlacklist.isEmpty()) return false;
        WrappedItemStack wrap = WrappedItemStack.wrap(item);
        for (Map<String, Object> map : vaultBlacklist) {
            if (new ItemMatcher(wrap, map).isMatching()) return true;
        }
        return false;
    }

    public boolean isOverLimit(@Nullable ItemStack incoming, @Nullable ItemStack outgoing, Inventory vault) {
        if (incoming == null || incoming.getType() == Material.AIR) return false;
        if (itemLimits.isEmpty()) return false;

        String incomingName = incoming.getType().name();

        for (Map<String, Object> rule : itemLimits) {
            String match = (String) rule.get("match");
            if (match == null || !SimpleRegex.matches(match, incomingName)) continue;

            String exempt = (String) rule.get("exempt");
            if (exempt != null && SimpleRegex.matches(exempt, incomingName)) continue;

            int max = rule.containsKey("max") ? ((Number) rule.get("max")).intValue() : -1;
            if (max < 0) continue;

            boolean perMaterial = !rule.containsKey("per-material") || Boolean.TRUE.equals(rule.get("per-material"));

            int count = 0;
            for (ItemStack slot : vault.getContents()) {
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (perMaterial ? slot.getType() == incoming.getType()
                                : SimpleRegex.matches(match, slot.getType().name())) {
                    count++;
                }
            }

            if (outgoing != null && !outgoing.getType().isAir()) {
                boolean outCounts = perMaterial ? outgoing.getType() == incoming.getType()
                                                : SimpleRegex.matches(match, outgoing.getType().name());
                if (outCounts) count--;
            }

            if (count >= max) return true;
        }
        return false;
    }
}
