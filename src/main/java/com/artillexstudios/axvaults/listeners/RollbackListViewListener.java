package com.artillexstudios.axvaults.listeners;

import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axvaults.commands.subcommands.Rollback;
import com.artillexstudios.axvaults.database.VaultBackup;
import com.artillexstudios.axvaults.vaults.RollbackListView;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RollbackListViewListener implements Listener {

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(PaperUtils.getHolder(event.getInventory(), false) instanceof RollbackListView list)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        int slot = event.getSlot();
        List<VaultBackup> backups = list.getBackups();
        if (slot < 0 || slot >= backups.size()) return;

        int index = slot + 1; // newest first
        OfflinePlayer target = Bukkit.getOfflinePlayer(list.getTargetUuid());

        if (event.isShiftClick()) {
            admin.closeInventory();
            Rollback.INSTANCE.execute(admin, target, list.getVaultId(), index);
        } else {
            admin.closeInventory();
            Rollback.INSTANCE.executeView(admin, target, list.getVaultId(), index);
        }
    }
}
