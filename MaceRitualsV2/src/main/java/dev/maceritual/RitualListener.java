package dev.maceritual;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class RitualListener implements Listener {

    private final MaceRituals plugin;

    public RitualListener(MaceRituals plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!plugin.rituals().isInRitual(p.getUniqueId())) return;
        if (plugin.getConfig().getBoolean("ritual.keep-inventory-on-death", true)) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        plugin.rituals().playerLost(p, "You fell during the ritual.");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location l = plugin.rituals().popRespawn(e.getPlayer().getUniqueId());
        if (l != null) e.setRespawnLocation(l);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.rituals().playerLost(e.getPlayer(), "You left the ritual.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.rituals().isInRitual(p.getUniqueId())) return;
        if (p.hasPermission("maceritual.bypass")) return;

        String cmd = e.getMessage().substring(1).toLowerCase(Locale.ROOT).split(" ")[0];
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        List<String> allowed = plugin.getConfig().getStringList("ritual.allowed-commands");
        for (String a : allowed) {
            if (a.equalsIgnoreCase(cmd)) return;
        }
        e.setCancelled(true);
        plugin.msg(p, "&cYou cannot use commands during a ritual.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!plugin.rituals().isInRitual(p.getUniqueId())) return;
        if (p.hasPermission("maceritual.bypass")) return;
        // the plugin's own teleports use the PLUGIN cause
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(plugin.rituals().mobKey(), PersistentDataType.BYTE)) return;
        if (plugin.getConfig().getBoolean("ritual.no-mob-drops", true)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}
