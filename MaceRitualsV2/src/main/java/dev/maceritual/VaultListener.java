package dev.maceritual;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Adds a custom heavy core to ominous trial vault loot with a low chance. */
public final class VaultListener implements Listener {

    private final MaceRituals plugin;

    public VaultListener(MaceRituals plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(BlockDispenseLootEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.VAULT) return;

        if (plugin.getConfig().getBoolean("vault.require-ominous", true)) {
            if (!(b.getBlockData() instanceof Vault v) || !v.isOminous()) return;
        }

        Player p = e.getPlayer();
        if (p == null) return;

        double chance = plugin.getConfig().getDouble("vault.chance", 0.02);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        MaceManager.MaceType type = plugin.maces().pickRandom();
        if (type == null) return;

        List<ItemStack> loot = new ArrayList<>(e.getDispensedLoot());
        loot.add(plugin.maces().buildCore(type, 1));
        e.setDispensedLoot(loot);

        plugin.msg(p, "&dThe vault yielded a " + type.coreName() + "&d!");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
    }
}
