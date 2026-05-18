package com.artillexstudios.axvaults.vaults;

import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axvaults.utils.BlacklistUtils;
import com.artillexstudios.axvaults.utils.ItemMatcher;
import com.artillexstudios.axvaults.utils.SimpleRegex;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;

public class VaultRules {

    private final int vaultId;

    public VaultRules(int vaultId) {
        this.vaultId = vaultId;
    }

    public boolean hasOverride() {
        return CONFIG.getSection("vault-overrides." + vaultId) != null;
    }

    /** True if this item should be blocked from entering/leaving the vault. */
    public boolean isBlacklisted(@Nullable ItemStack item) {
        if (!hasOverride()) return BlacklistUtils.isBlacklisted(item);
        if (CONFIG.getBoolean("vault-overrides." + vaultId + ".bypass-global-blacklist", false)) {
            return isVaultBlacklisted(item);
        }
        return BlacklistUtils.isBlacklisted(item) || isVaultBlacklisted(item);
    }

    private boolean isVaultBlacklisted(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        List<Map<String, Object>> list = CONFIG.getMapList("vault-overrides." + vaultId + ".blacklist-items");
        if (list == null || list.isEmpty()) return false;
        WrappedItemStack wrap = WrappedItemStack.wrap(item);
        for (Map<String, Object> map : list) {
            if (new ItemMatcher(wrap, map).isMatching()) return true;
        }
        return false;
    }

    /**
     * True if placing {@code incoming} into the vault would exceed an item-limit rule.
     * Pass {@code outgoing} (the vault slot item being displaced) so swaps are handled correctly.
     */
    public boolean isOverLimit(@Nullable ItemStack incoming, @Nullable ItemStack outgoing, Inventory vault) {
        if (incoming == null || incoming.getType() == Material.AIR) return false;
        List<Map<String, Object>> limits = CONFIG.getMapList("vault-overrides." + vaultId + ".item-limits");
        if (limits == null || limits.isEmpty()) return false;

        String incomingName = incoming.getType().name();

        for (Map<String, Object> rule : limits) {
            String match = (String) rule.get("match");
            if (match == null || !SimpleRegex.matches(match, incomingName)) continue;

            // skip exempt materials (e.g. leather armor)
            String exempt = (String) rule.get("exempt");
            if (exempt != null && SimpleRegex.matches(exempt, incomingName)) continue;

            int max = rule.containsKey("max") ? ((Number) rule.get("max")).intValue() : -1;
            if (max < 0) continue;

            // per-material: count only same exact material (default true)
            // per-material: false: count all items matching the pattern
            boolean perMaterial = !rule.containsKey("per-material") || Boolean.TRUE.equals(rule.get("per-material"));

            int count = 0;
            for (ItemStack slot : vault.getContents()) {
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (perMaterial ? slot.getType() == incoming.getType()
                                : SimpleRegex.matches(match, slot.getType().name())) {
                    count++;
                }
            }

            // A swap frees one slot of the outgoing item — don't penalise the player for it
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
