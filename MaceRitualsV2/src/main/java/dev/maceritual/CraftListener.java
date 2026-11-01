package dev.maceritual;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Heavy Core + Breeze Rod in the 2x2 inventory crafting grid shows the mace as a preview.
 * Clicking the result consumes both items and starts a ritual instead of giving the mace.
 */
public final class CraftListener implements Listener {

    private final MaceRituals plugin;

    public CraftListener(MaceRituals plugin) {
        this.plugin = plugin;
    }

    /** Returns the core type if the grid holds exactly one custom core + one breeze rod and nothing else. */
    private MaceManager.MaceType match(ItemStack[] matrix) {
        MaceManager.MaceType type = null;
        int cores = 0, rods = 0;
        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() == Material.BREEZE_ROD) {
                rods++;
                continue;
            }
            MaceManager.MaceType t = plugin.maces().getCoreType(it);
            if (t == null) return null;
            type = t;
            cores++;
        }
        return (cores == 1 && rods == 1) ? type : null;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        // Crafting tables: custom cores must never turn into a vanilla mace
        if (matrix.length != 4) {
            for (ItemStack it : matrix) {
                if (plugin.maces().getCoreType(it) != null) {
                    inv.setResult(null);
                    return;
                }
            }
            return;
        }

        MaceManager.MaceType type = match(matrix);
        if (type == null) {
            // vanilla mace recipe would accept a custom core laid out vertically; block it
            for (ItemStack it : matrix) {
                if (plugin.maces().getCoreType(it) != null) {
                    inv.setResult(null);
                    return;
                }
            }
            return;
        }

        if (!(e.getView().getPlayer() instanceof Player p) || !p.hasPermission("maceritual.use")) {
            inv.setResult(null);
            return;
        }

        ItemStack preview = plugin.maces().buildMace(type);
        ItemMeta meta = preview.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.add(MaceRituals.color(""));
        lore.add(MaceRituals.color("&5&l▸ Click to begin the ritual"));
        meta.lore(lore);
        preview.setItemMeta(meta);
        inv.setResult(preview);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof CraftingInventory inv)) return;
        if (inv.getMatrix().length != 4) return;
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        MaceManager.MaceType type = match(inv.getMatrix());
        if (type == null) return;

        // never let the preview mace reach the cursor/inventory
        e.setCancelled(true);

        ClickType ct = e.getClick();
        if (ct != ClickType.LEFT && ct != ClickType.RIGHT
                && ct != ClickType.SHIFT_LEFT && ct != ClickType.SHIFT_RIGHT) return;

        if (!p.hasPermission("maceritual.use")) {
            plugin.msg(p, "&cYou do not have permission to start rituals.");
            return;
        }
        if (plugin.rituals().isInRitual(p.getUniqueId())) {
            plugin.msg(p, "&cYou are already in a ritual.");
            return;
        }
        if (!plugin.rituals().canStart()) {
            plugin.msg(p, "&cNo ritual arena is free right now. Try again soon.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume one core + one breeze rod
        ItemStack[] m = inv.getMatrix();
        for (int i = 0; i < m.length; i++) {
            ItemStack it = m[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
            } else {
                m[i] = null;
            }
        }
        inv.setMatrix(m);
        inv.setResult(null);

        p.closeInventory();
        if (!plugin.rituals().start(p, type)) {
            // should not happen (checked above) but never eat the items
            p.getInventory().addItem(plugin.maces().buildCore(type, 1));
            p.getInventory().addItem(new ItemStack(Material.BREEZE_ROD));
            plugin.msg(p, "&cThe ritual could not start. Your items were returned.");
        }
        plugin.getServer().getScheduler().runTask(plugin, p::updateInventory);
    }

    /** Safety net: nothing custom should ever craft normally from the result slot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getInventory().getMatrix().length == 4 && match(e.getInventory().getMatrix()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.maces().getCoreType(e.getItemInHand()) != null) {
            e.setCancelled(true);
        }
    }
}
