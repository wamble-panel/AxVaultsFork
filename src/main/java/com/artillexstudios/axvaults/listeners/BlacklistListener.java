package com.artillexstudios.axvaults.listeners;

import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axvaults.utils.BlacklistUtils;
import com.artillexstudios.axvaults.vaults.Vault;
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
        if (!(PaperUtils.getHolder(event.getInventory(), false) instanceof Vault)) return;
        if (event.getClickedInventory() == null) return;

        Inventory vault = event.getView().getTopInventory();
        boolean clickedVault = event.getClickedInventory().equals(vault);

        // Always block blacklisted items going INTO the vault
        if (BlacklistUtils.isBlacklisted(getItemGoingIn(event, vault, clickedVault))) {
            event.setCancelled(true);
            MESSAGEUTILS.sendLang(event.getWhoClicked(), "banned-item");
            return;
        }

        // Optionally block taking blacklisted items OUT of the vault
        if (!CONFIG.getBoolean("blacklist-allow-taking-out", true)
                && BlacklistUtils.isBlacklisted(getItemGoingOut(event, vault, clickedVault))) {
            event.setCancelled(true);
            MESSAGEUTILS.sendLang(event.getWhoClicked(), "banned-item");
        }
    }

    // Returns the item that would be placed INTO the vault, or null if nothing is going in.
    @Nullable
    private ItemStack getItemGoingIn(InventoryClickEvent event, Inventory vault, boolean clickedVault) {
        ClickType click = event.getClick();

        if (click == ClickType.SWAP_OFFHAND && clickedVault) {
            // offhand item swapped into the vault slot
            return event.getWhoClicked().getInventory().getItemInOffHand();
        }

        if (click == ClickType.NUMBER_KEY && clickedVault) {
            // hotbar item swapped into the vault slot
            return event.getView().getBottomInventory().getItem(event.getHotbarButton());
        }

        if (!clickedVault && event.isShiftClick()) {
            // shift-click from player inventory sends item into vault
            return event.getCurrentItem();
        }

        if (clickedVault && !event.isShiftClick()) {
            // placing cursor item into a vault slot
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) return cursor;
        }

        return null;
    }

    // Returns the item that would be taken OUT of the vault, or null if nothing is going out.
    @Nullable
    private ItemStack getItemGoingOut(InventoryClickEvent event, Inventory vault, boolean clickedVault) {
        if (!clickedVault) return null;

        ClickType click = event.getClick();

        if (click == ClickType.NUMBER_KEY) {
            // vault slot item swapped to hotbar
            return event.getCurrentItem();
        }

        if (click == ClickType.SWAP_OFFHAND) {
            // vault slot item swapped to offhand
            return event.getCurrentItem();
        }

        // shift-click from vault or regular pick-up from vault slot
        return event.getCurrentItem();
    }
}
