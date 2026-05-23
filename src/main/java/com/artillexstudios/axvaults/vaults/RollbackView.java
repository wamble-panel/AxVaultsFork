package com.artillexstudios.axvaults.vaults;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RollbackView implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUuid;
    private final int vaultId;
    private final int backupIndex;

    public RollbackView(UUID targetUuid, int vaultId, int backupIndex, ItemStack[] contents, String title) {
        int size = ((Math.max(contents != null ? contents.length : 0, 1) + 8) / 9) * 9;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        this.inventory = Bukkit.createInventory(this, size, title);
        this.targetUuid = targetUuid;
        this.vaultId = vaultId;
        this.backupIndex = backupIndex;
        if (contents != null) {
            for (int i = 0; i < contents.length && i < this.inventory.getSize(); i++) {
                this.inventory.setItem(i, contents[i]);
            }
        }
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public int getVaultId() {
        return vaultId;
    }

    public int getBackupIndex() {
        return backupIndex;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
