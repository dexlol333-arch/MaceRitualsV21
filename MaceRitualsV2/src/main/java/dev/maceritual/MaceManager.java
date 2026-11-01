package dev.maceritual;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MaceManager {

    public record MaceType(String id, String maceName, List<String> maceLore,
                           String coreName, List<String> coreLore, int weight,
                           Map<Enchantment, Integer> enchants, double bonusDamage, int fireSeconds,
                           List<PotionEffect> potions, double lightningChance,
                           double shockRadius, double shockDamage,
                           String abilityType, String abilityName, int abilityCooldown,
                           double abilityRadius, double abilityDamage) {}

    private final MaceRituals plugin;
    private final NamespacedKey coreKey;
    private final NamespacedKey maceKey;
    private final Map<String, MaceType> types = new LinkedHashMap<>();

    public MaceManager(MaceRituals plugin) {
        this.plugin = plugin;
        this.coreKey = new NamespacedKey(plugin, "core_type");
        this.maceKey = new NamespacedKey(plugin, "mace_type");
        load();
    }

    public void load() {
        types.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("maces");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            Map<Enchantment, Integer> ench = new LinkedHashMap<>();
            ConfigurationSection es = s.getConfigurationSection("enchants");
            if (es != null) {
                for (String k : es.getKeys(false)) {
                    Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
                    if (e == null) {
                        plugin.getLogger().warning("[" + id + "] Unknown enchantment: " + k);
                        continue;
                    }
                    ench.put(e, es.getInt(k));
                }
            }

            List<PotionEffect> pots = new ArrayList<>();
            for (String raw : s.getStringList("potions")) {
                String[] p = raw.split(":");
                PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(p[0].toLowerCase(Locale.ROOT)));
                if (t == null) {
                    plugin.getLogger().warning("[" + id + "] Unknown potion effect: " + p[0]);
                    continue;
                }
                int dur = p.length > 1 ? parse(p[1], 100) : 100;
                int amp = p.length > 2 ? parse(p[2], 0) : 0;
                pots.add(new PotionEffect(t, dur, amp));
            }

            ConfigurationSection ab = s.getConfigurationSection("ability");
            String abType = ab == null ? "NONE" : ab.getString("type", "NONE").toUpperCase(Locale.ROOT);
            String abName = ab == null ? "" : ab.getString("name", "&fAbility");
            int abCd = ab == null ? 0 : ab.getInt("cooldown", 15);
            double abRad = ab == null ? 0 : ab.getDouble("radius", 6);
            double abDmg = ab == null ? 0 : ab.getDouble("damage", 6);

            types.put(id.toLowerCase(Locale.ROOT), new MaceType(
                    id.toLowerCase(Locale.ROOT),
                    s.getString("mace-name", "&cCustom Mace"),
                    s.getStringList("mace-lore"),
                    s.getString("core-name", "&6Custom Heavy Core"),
                    s.getStringList("core-lore"),
                    Math.max(1, s.getInt("weight", 1)),
                    ench,
                    s.getDouble("bonus-damage", 0),
                    s.getInt("fire-seconds", 0),
                    pots,
                    s.getDouble("lightning-chance", 0),
                    s.getDouble("shockwave-radius", 0),
                    s.getDouble("shockwave-damage", 0),
                    abType, abName, abCd, abRad, abDmg));
        }
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    public Collection<MaceType> all() { return types.values(); }
    public MaceType get(String id) { return id == null ? null : types.get(id.toLowerCase(Locale.ROOT)); }

    public MaceType pickRandom() {
        int total = 0;
        for (MaceType t : types.values()) total += t.weight();
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (MaceType t : types.values()) {
            roll -= t.weight();
            if (roll < 0) return t;
        }
        return null;
    }

    public ItemStack buildCore(MaceType t, int amount) {
        ItemStack it = new ItemStack(Material.HEAVY_CORE, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(MaceRituals.color(t.coreName()));
        m.lore(t.coreLore().stream().map(MaceRituals::color).toList());
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(coreKey, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        return it;
    }

    public ItemStack buildMace(MaceType t) {
        ItemStack it = new ItemStack(Material.MACE);
        ItemMeta m = it.getItemMeta();
        m.displayName(MaceRituals.color(t.maceName()));
        m.lore(t.maceLore().stream().map(MaceRituals::color).toList());
        for (Map.Entry<Enchantment, Integer> e : t.enchants().entrySet()) {
            m.addEnchant(e.getKey(), e.getValue(), true);
        }
        m.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        return it;
    }

    public MaceType getCoreType(ItemStack it) {
        if (it == null || it.getType() != Material.HEAVY_CORE || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(coreKey, PersistentDataType.STRING);
        return id == null ? null : types.get(id);
    }

    public MaceType getMaceType(ItemStack it) {
        if (it == null || it.getType() != Material.MACE || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(maceKey, PersistentDataType.STRING);
        return id == null ? null : types.get(id);
    }
}
