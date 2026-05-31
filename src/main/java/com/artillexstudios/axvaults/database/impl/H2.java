package com.artillexstudios.axvaults.database.impl;

import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.database.Database;
import com.artillexstudios.axvaults.database.VaultBackup;
import com.artillexstudios.axvaults.placed.PlacedVaults;
import com.artillexstudios.axvaults.utils.SerializationUtils;
import com.artillexstudios.axvaults.utils.ThreadUtils;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.h2.jdbc.JdbcConnection;

import java.io.ByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class H2 implements Database {
    private JdbcConnection conn;

    @Override
    public String getType() {
        return "H2";
    }

    @Override
    public void setup() {
        try {
            conn = new JdbcConnection("jdbc:h2:./" + AxVaults.getInstance().getDataFolder() + "/data;mode=MySQL", new Properties(), null, null, false);
            conn.setAutoCommit(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String CREATE_TABLE = """
                    CREATE TABLE IF NOT EXISTS `axvaults_data`(
                      `id` INT(128) NOT NULL,
                      `uuid` VARCHAR(36) NOT NULL,
                      `storage` LONGBLOB,
                      `icon` VARCHAR(128)
                    );
                """;

        try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        String CREATE_TABLE2 = """
                    CREATE TABLE IF NOT EXISTS `axvaults_blocks` (
                      `location` VARCHAR(255) NOT NULL,
                      `number` INT,
                      PRIMARY KEY (`location`)
                    );
                """;

        try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE2)) {
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        String CREATE_BACKUPS_TABLE = """
            CREATE TABLE IF NOT EXISTS `axvaults_backups` (
              `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
              `vault_id` INT NOT NULL,
              `uuid` VARCHAR(36) NOT NULL,
              `storage` LONGBLOB,
              `icon` VARCHAR(128),
              `backed_up_at` BIGINT NOT NULL
            );
""";
        try (PreparedStatement stmt = conn.prepareStatement(CREATE_BACKUPS_TABLE)) {
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void backupCurrentData(UUID uuid, int vaultId) {
        if (!com.artillexstudios.axvaults.AxVaults.CONFIG.getBoolean("vault-rollback.enabled", true)) return;
        final String selectSql = "SELECT storage, icon FROM axvaults_data WHERE uuid = ? AND id = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, vaultId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return;
                byte[] storage = rs.getBytes(1);
                String icon = rs.getString(2);
                if (storage == null) return;
                final String insertSql = "INSERT INTO axvaults_backups(vault_id, uuid, storage, icon, backed_up_at) VALUES (?, ?, ?, ?, ?);";
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setInt(1, vaultId);
                    ins.setString(2, uuid.toString());
                    ins.setBytes(3, storage);
                    ins.setString(4, icon);
                    ins.setLong(5, System.currentTimeMillis());
                    ins.executeUpdate();
                }
                // prune: keep only the N most recent backups
                int max = com.artillexstudios.axvaults.AxVaults.CONFIG.getInt("vault-rollback.max-backups-per-vault", 5);
                final String listSql = "SELECT id FROM axvaults_backups WHERE uuid = ? AND vault_id = ? ORDER BY backed_up_at DESC;";
                try (PreparedStatement listStmt = conn.prepareStatement(listSql)) {
                    listStmt.setString(1, uuid.toString());
                    listStmt.setInt(2, vaultId);
                    try (ResultSet listRs = listStmt.executeQuery()) {
                        List<Long> ids = new ArrayList<>();
                        while (listRs.next()) ids.add(listRs.getLong(1));
                        for (int i = max; i < ids.size(); i++) {
                            try (PreparedStatement del = conn.prepareStatement("DELETE FROM axvaults_backups WHERE id = ?;")) {
                                del.setLong(1, ids.get(i));
                                del.executeUpdate();
                            }
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void saveVault(Vault vault, Object result) {
        // delete empty vaults
        if (result instanceof Boolean bool && bool) {
            backupCurrentData(vault.getUUID(), vault.getId());
            String sql = "DELETE FROM axvaults_data WHERE uuid = ? AND id = ?;";
            try (PreparedStatement stmt = conn.prepareStatement(sql)){
                stmt.setString(1, vault.getUUID().toString());
                stmt.setInt(2, vault.getId());
                stmt.executeUpdate();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return;
        }

        if (result == null) {
            Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF0000[AxVaults] Failed to save vault #%s of %s!".formatted(vault.getId(), vault.getUUID().toString())));
            return;
        }

        byte[] bytes = (byte[]) result;
        String sql = "SELECT * FROM axvaults_data WHERE uuid = ? AND id = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vault.getUUID().toString());
            stmt.setInt(2, vault.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    backupCurrentData(vault.getUUID(), vault.getId());
                    sql = "UPDATE axvaults_data SET storage = ?, icon = ? WHERE uuid = ? AND id = ?;";
                    try (PreparedStatement stmt2 = conn.prepareStatement(sql)) {
                        stmt2.setBytes(1, bytes);
                        stmt2.setString(2, vault.getRealIcon() == null ? null : vault.getRealIcon().name());
                        stmt2.setString(3, vault.getUUID().toString());
                        stmt2.setInt(4, vault.getId());
                        stmt2.executeUpdate();
                    }
                } else {
                    sql = "INSERT INTO axvaults_data(id, uuid, storage, icon) VALUES (?, ?, ?, ?);";
                    try (PreparedStatement stmt2 = conn.prepareStatement(sql)) {
                        stmt2.setInt(1, vault.getId());
                        stmt2.setString(2, vault.getUUID().toString());
                        stmt2.setBytes(3, bytes);
                        stmt2.setString(4, vault.getRealIcon() == null ? null : vault.getRealIcon().name());
                        stmt2.executeUpdate();
                    }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void loadVaults(@NotNull VaultPlayer vaultPlayer) {
        final String sql = "SELECT * FROM axvaults_data WHERE uuid = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vaultPlayer.getUUID().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    byte[] bytes = rs.getBytes(3);
                    ItemStack[] tempItems = null;
                    boolean legacy = false;
                    try {
                        tempItems = Serializers.ITEM_ARRAY.deserialize(bytes);
                    } catch (Exception ex) {
                        // fallback 1: legacy BukkitObjectOutputStream (pre-2.0.0) format
                        tempItems = SerializationUtils.invFromBits(new ByteArrayInputStream(bytes));
                        if (tempItems != null) {
                            legacy = true;
                            Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FFAA00[AxVaults] Migrated legacy vault #%s of %s to new format.".formatted(id, vaultPlayer.getUUID().toString())));
                        } else {
                            // fallback 2: intermediate axapi format (4-byte count + 2-byte short + raw item bytes)
                            tempItems = SerializationUtils.tryManualDeserialize(bytes);
                            if (tempItems != null) {
                                Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FFAA00[AxVaults] Recovered vault #%s of %s via manual fallback — verify contents in-game.".formatted(id, vaultPlayer.getUUID().toString())));
                            }
                        }
                        if (tempItems == null) {
                            ex.printStackTrace();
                            Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF0000[AxVaults] Failed to load vault #%s of %s!".formatted(id, vaultPlayer.getUUID().toString())));
                            continue;
                        }
                    }
//                    if (VaultUtils.isDeleteEmptyVaults() && items.length == 0) continue;
                    final ItemStack[] items = tempItems;
                    final Material icon = rs.getString(4) == null ? null : Material.valueOf(rs.getString(4));
                    final boolean needsMigration = legacy;
                    ThreadUtils.runSync(() -> {
                        Vault vault = new Vault(vaultPlayer, id, icon, items);
                        if (needsMigration) vault.hasChanged().set(true);
                    });
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean isVault(@NotNull Location location) {
        final String sql = "SELECT * FROM axvaults_blocks WHERE location = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Serializers.LOCATION.serialize(location));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    @Override
    public void setVault(@NotNull Location location, @Nullable Integer num) {
        final String sql = "INSERT INTO `axvaults_blocks`(`location`, `number`) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Serializers.LOCATION.serialize(location));
            if (num == null) stmt.setString(2, null);
            else stmt.setInt(2, num);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        PlacedVaults.addVault(location, num);
    }

    @Override
    public void removeVault(@NotNull Location location) {
        final String sql = "DELETE FROM axvaults_blocks WHERE location = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Serializers.LOCATION.serialize(location));
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void deleteVault(@NotNull UUID uuid, int num) {
        backupCurrentData(uuid, num);
        final String sql = "DELETE FROM axvaults_data WHERE uuid = ? AND id = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, num);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public List<VaultBackup> getBackups(UUID uuid, int vaultId) {
        List<VaultBackup> result = new ArrayList<>();
        final String sql = "SELECT id, vault_id, uuid, storage, icon, backed_up_at FROM axvaults_backups WHERE uuid = ? AND vault_id = ? ORDER BY backed_up_at DESC;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, vaultId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new VaultBackup(
                        rs.getLong(1), rs.getInt(2), UUID.fromString(rs.getString(3)),
                        rs.getBytes(4), rs.getString(5), rs.getLong(6)
                    ));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    @Override
    public boolean restoreBackup(VaultBackup backup) {
        final String checkSql = "SELECT COUNT(*) FROM axvaults_data WHERE uuid = ? AND id = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, backup.uuid.toString());
            stmt.setInt(2, backup.vaultId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                boolean exists = rs.getInt(1) > 0;
                String sql = exists
                    ? "UPDATE axvaults_data SET storage = ?, icon = ? WHERE uuid = ? AND id = ?;"
                    : "INSERT INTO axvaults_data(storage, icon, uuid, id) VALUES (?, ?, ?, ?);";
                try (PreparedStatement upsert = conn.prepareStatement(sql)) {
                    upsert.setBytes(1, backup.storage);
                    upsert.setString(2, backup.icon);
                    upsert.setString(3, backup.uuid.toString());
                    upsert.setInt(4, backup.vaultId);
                    upsert.executeUpdate();
                }
            }
            return true;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public void load() {
        final String sql = "SELECT * FROM axvaults_blocks;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String vault = rs.getString(2);
                    final Integer vaultInt = vault == null ? null : Integer.parseInt(vault);
                    PlacedVaults.addVault(Serializers.LOCATION.deserialize(rs.getString(1)), vaultInt);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void disable() {
        try {
            conn.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
