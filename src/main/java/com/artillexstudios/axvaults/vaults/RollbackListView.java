package com.artillexstudios.axvaults.vaults;

import com.artillexstudios.axvaults.database.VaultBackup;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class RollbackListView implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUuid;
    private final int vaultId;
    private final List<VaultBackup> backups;

    public RollbackListView(UUID targetUuid, int vaultId, List<VaultBackup> backups, String title) {
        int size = ((Math.max(backups.size(), 1) + 8) / 9) * 9;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        this.inventory = Bukkit.createInventory(this, size, title);
        this.targetUuid = targetUuid;
        this.vaultId = vaultId;
        this.backups = backups;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public int getVaultId() {
        return vaultId;
    }

    public List<VaultBackup> getBackups() {
        return backups;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
