package com.artillexstudios.axvaults.listeners;

import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultRules;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;
import static com.artillexstudios.axvaults.AxVaults.MESSAGEUTILS;

public class BlacklistListener implements Listener {

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(PaperUtils.getHolder(event.getInventory(), false) instanceof Vault vault)) return;
        if (event.getClickedInventory() == null) return;

        VaultRules rules = new VaultRules(vault.getId());
        Inventory vaultInv = event.getView().getTopInventory();
        boolean clickedVault = event.getClickedInventory().equals(vaultInv);

        ItemStack goingIn  = getItemGoingIn(event, clickedVault);
        ItemStack goingOut = getItemGoingOut(event, clickedVault);

        // Always block blacklisted items going into the vault
        if (rules.isBlacklisted(goingIn)) {
            event.setCancelled(true);
            MESSAGEUTILS.sendLang(event.getWhoClicked(), "banned-item");
            return;
        }

        // Block if the item limit for this vault would be exceeded
        if (goingIn != null && rules.isOverLimit(goingIn, goingOut, vaultInv)) {
            event.setCancelled(true);
            MESSAGEUTILS.sendLang(event.getWhoClicked(), "vault-item-limit");
            return;
        }

        // Optionally block taking blacklisted items out of the vault
        if (!CONFIG.getBoolean("blacklist-allow-taking-out", true) && rules.isBlacklisted(goingOut)) {
            event.setCancelled(true);
            MESSAGEUTILS.sendLang(event.getWhoClicked(), "banned-item");
        }
    }

    @Nullable
    private ItemStack getItemGoingIn(InventoryClickEvent event, boolean clickedVault) {
        ClickType click = event.getClick();

        if (click == ClickType.SWAP_OFFHAND && clickedVault)
            return event.getWhoClicked().getInventory().getItemInOffHand();

        if (click == ClickType.NUMBER_KEY && clickedVault)
            return event.getView().getBottomInventory().getItem(event.getHotbarButton());

        if (!clickedVault && event.isShiftClick())
            return event.getCurrentItem();

        if (clickedVault && !event.isShiftClick()) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) return cursor;
        }

        return null;
    }

    @Nullable
    private ItemStack getItemGoingOut(InventoryClickEvent event, boolean clickedVault) {
        if (!clickedVault) return null;
        return event.getCurrentItem();
    }
}
