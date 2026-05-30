package com.artillexstudios.axvaults.commands.subcommands;

import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.database.VaultBackup;
import com.artillexstudios.axvaults.utils.SerializationUtils;
import com.artillexstudios.axvaults.utils.ThreadUtils;
import com.artillexstudios.axvaults.vaults.RollbackListView;
import com.artillexstudios.axvaults.vaults.RollbackView;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultManager;
import com.artillexstudios.axvaults.vaults.VaultPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.artillexstudios.axvaults.AxVaults.MESSAGEUTILS;

public enum Rollback {
    INSTANCE;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public void execute(CommandSender sender, OfflinePlayer player, int vaultId, int index) {
        Map<String, String> rep = replacements(player, vaultId);
        rep.put("%index%", String.valueOf(index));

        AxVaults.getThreadedQueue().submit(() -> {
            List<VaultBackup> backups = AxVaults.getDatabase().getBackups(player.getUniqueId(), vaultId);
            if (backups.isEmpty() || index < 1 || index > backups.size()) {
                ThreadUtils.runSync(() -> MESSAGEUTILS.sendLang(sender, "rollback.not-found", rep));
                return;
            }
            VaultBackup backup = backups.get(index - 1);
            if (!AxVaults.getDatabase().restoreBackup(backup)) {
                ThreadUtils.runSync(() -> MESSAGEUTILS.sendLang(sender, "rollback.error", rep));
                return;
            }
            ThreadUtils.runSync(() -> {
                // Update in-memory vault if loaded
                VaultPlayer vp = VaultManager.getPlayerOrNull(Bukkit.getOfflinePlayer(player.getUniqueId()));
                if (vp != null) {
                    Vault vault = vp.getVaultMap().get(vaultId);
                    if (vault != null) {
                        ItemStack[] items = deserialize(backup.storage);
                        if (items != null) {
                            vault.setContents(items);
                            vault.hasChanged().set(false);
                        }
                    }
                }
                MESSAGEUTILS.sendLang(sender, "rollback.success", rep);
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) MESSAGEUTILS.sendLang(online, "rollback.restored-online", rep);
            });
        });
    }

    public void executeView(CommandSender sender, OfflinePlayer player, int vaultId, int index) {
        Map<String, String> rep = replacements(player, vaultId);
        rep.put("%index%", String.valueOf(index));

        if (!(sender instanceof Player adminPlayer)) {
            MESSAGEUTILS.sendLang(sender, "commands.player-only", rep);
            return;
        }

        AxVaults.getThreadedQueue().submit(() -> {
            List<VaultBackup> backups = AxVaults.getDatabase().getBackups(player.getUniqueId(), vaultId);
            if (backups.isEmpty() || index < 1 || index > backups.size()) {
                ThreadUtils.runSync(() -> MESSAGEUTILS.sendLang(sender, "rollback.not-found", rep));
                return;
            }
            VaultBackup backup = backups.get(index - 1);
            ItemStack[] items = deserialize(backup.storage);
            if (items == null) {
                ThreadUtils.runSync(() -> MESSAGEUTILS.sendLang(sender, "rollback.error", rep));
                return;
            }
            ThreadUtils.runSync(() -> {
                String title = "Backup #" + index + " of " + (player.getName() != null ? player.getName() : "?") + " — Vault #" + vaultId;
                RollbackView view = new RollbackView(player.getUniqueId(), vaultId, index, items, title);
                adminPlayer.openInventory(view.getInventory());
            });
        });
    }

    public void executeList(CommandSender sender, OfflinePlayer player, int vaultId) {
        Map<String, String> rep = replacements(player, vaultId);

        if (!(sender instanceof Player adminPlayer)) {
            // fallback: text list for console
            AxVaults.getThreadedQueue().submit(() -> {
                List<VaultBackup> backups = AxVaults.getDatabase().getBackups(player.getUniqueId(), vaultId);
                ThreadUtils.runSync(() -> {
                    if (backups.isEmpty()) {
                        MESSAGEUTILS.sendLang(sender, "rollback.not-found", rep);
                        return;
                    }
                    MESSAGEUTILS.sendLang(sender, "rollback.list-header", rep);
                    for (int i = 0; i < backups.size(); i++) {
                        Map<String, String> entryRep = new HashMap<>(rep);
                        entryRep.put("%index%", String.valueOf(i + 1));
                        entryRep.put("%date%", DATE_FMT.format(Instant.ofEpochMilli(backups.get(i).backedUpAt)));
                        MESSAGEUTILS.sendLang(sender, "rollback.list-entry", entryRep);
                    }
                });
            });
            return;
        }

        AxVaults.getThreadedQueue().submit(() -> {
            List<VaultBackup> backups = AxVaults.getDatabase().getBackups(player.getUniqueId(), vaultId);
            ThreadUtils.runSync(() -> {
                if (backups.isEmpty()) {
                    MESSAGEUTILS.sendLang(sender, "rollback.not-found", rep);
                    return;
                }

                String playerName = player.getName() != null ? player.getName() : "?";
                String title = StringUtils.formatToString("&8" + playerName + " — Vault #" + vaultId + " Backups");
                RollbackListView view = new RollbackListView(player.getUniqueId(), vaultId, backups, title);

                for (int i = 0; i < backups.size() && i < view.getInventory().getSize(); i++) {
                    VaultBackup b = backups.get(i);
                    int itemCount = countItems(b.storage);
                    ItemStack icon = new ItemStack(i == 0 ? Material.CLOCK : Material.PAPER);
                    org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(StringUtils.formatToString("&#55ff00&lBackup #" + (i + 1) + (i == 0 ? " &7(newest)" : "")));
                        List<String> lore = new ArrayList<>();
                        lore.add(StringUtils.formatToString("&7Date: &f" + DATE_FMT.format(Instant.ofEpochMilli(b.backedUpAt))));
                        lore.add(StringUtils.formatToString("&7Items: &f" + itemCount));
                        lore.add(StringUtils.formatToString(" "));
                        lore.add(StringUtils.formatToString("&#55ff00&lLeft-Click &7to view contents"));
                        lore.add(StringUtils.formatToString("&#FFAA00&lShift-Click &7to restore"));
                        meta.setLore(lore);
                        icon.setItemMeta(meta);
                    }
                    view.getInventory().setItem(i, icon);
                }

                adminPlayer.openInventory(view.getInventory());
            });
        });
    }

    private int countItems(byte[] bytes) {
        ItemStack[] items = deserialize(bytes);
        if (items == null) return 0;
        int n = 0;
        for (ItemStack it : items) if (it != null && it.getType() != Material.AIR) n++;
        return n;
    }

    private ItemStack[] deserialize(byte[] bytes) {
        try {
            return Serializers.ITEM_ARRAY.deserialize(bytes);
        } catch (Exception e) {
            return SerializationUtils.invFromBits(new ByteArrayInputStream(bytes));
        }
    }

    private Map<String, String> replacements(OfflinePlayer player, int vaultId) {
        Map<String, String> map = new HashMap<>();
        map.put("%player%", player.getName() != null ? player.getName() : player.getUniqueId().toString());
        map.put("%num%", String.valueOf(vaultId));
        return map;
    }
}
