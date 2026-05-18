package com.artillexstudios.axvaults.database;

import org.bukkit.Material;
import java.util.UUID;

public class VaultBackup {
    public final long id;
    public final int vaultId;
    public final UUID uuid;
    public final byte[] storage;
    public final String icon;
    public final long backedUpAt;

    public VaultBackup(long id, int vaultId, UUID uuid, byte[] storage, String icon, long backedUpAt) {
        this.id = id;
        this.vaultId = vaultId;
        this.uuid = uuid;
        this.storage = storage;
        this.icon = icon;
        this.backedUpAt = backedUpAt;
    }
}
