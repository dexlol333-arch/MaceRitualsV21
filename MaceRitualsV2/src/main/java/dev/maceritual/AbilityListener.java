package dev.maceritual;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Passive on-hit effects plus a right-click active ability for each ritual mace. */
public final class AbilityListener implements Listener {

    private final MaceRituals plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean inAbility = false;

    public AbilityListener(MaceRituals plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Passive on-hit effects
    // ------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (inAbility) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        MaceManager.MaceType t = plugin.maces().getMaceType(p.getInventory().getItemInMainHand());
        if (t == null) return;

        if (t.bonusDamage() > 0) e.setDamage(e.getDamage() + t.bonusDamage());
        if (t.fireSeconds() > 0) target.setFireTicks(t.fireSeconds() * 20);
        for (PotionEffect pe : t.potions()) target.addPotionEffect(pe);

        if (t.lightningChance() > 0 && ThreadLocalRandom.current().nextDouble() < t.lightningChance()) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(4.0); // no source, so it cannot re-trigger this event
        }

        // smash shockwave (falling hit) - hurts and launches nearby mobs, never players
        if (t.shockRadius() > 0 && p.getFallDistance() > 1.5f) {
            double r = t.shockRadius();
            for (Entity near : target.getNearbyEntities(r, r, r)) {
                if (near instanceof Player || near.equals(p) || !(near instanceof LivingEntity le)) continue;
                le.damage(t.shockDamage());
                Vector away = le.getLocation().toVector().subtract(target.getLocation().toVector());
                away.setY(0);
                if (away.lengthSquared() > 0) away.normalize().multiply(0.6);
                away.setY(0.4);
                le.setVelocity(away);
            }
            target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 40, r / 2, 0.2, r / 2, 0.05);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------
    // Active right-click ability
    // ------------------------------------------------------------------
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        MaceManager.MaceType t = plugin.maces().getMaceType(p.getInventory().getItemInMainHand());
        if (t == null || t.abilityType().equals("NONE")) return;

        String key = p.getUniqueId() + ":" + t.id();
        long now = System.currentTimeMillis();
        long ready = cooldowns.getOrDefault(key, 0L);
        if (now < ready) {
            long secs = (ready - now + 999) / 1000;
            p.sendActionBar(MaceRituals.color(t.abilityName() + " &7ready in &e" + secs + "s"));
            return;
        }

        List<LivingEntity> targets = targets(p, t.abilityRadius());
        if (targets.isEmpty()) {
            p.sendActionBar(MaceRituals.color("&7No enemies in range for " + t.abilityName()));
            return;
        }

        switch (t.abilityType()) {
            case "FLAME_BURST" -> flameBurst(p, t, targets);
            case "STORM_STRIKE" -> stormStrike(p, t, targets);
            case "VOID_PULL" -> voidPull(p, t, targets);
            default -> {
                return;
            }
        }
        cooldowns.put(key, now + t.abilityCooldown() * 1000L);
        p.sendActionBar(MaceRituals.color(t.abilityName() + " &aactivated!"));
    }

    private List<LivingEntity> targets(Player p, double r) {
        boolean hitPlayers = plugin.getConfig().getBoolean("abilities.hit-players", false);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand) continue;
            if (le.equals(p)) continue;
            if (le instanceof Player && !hitPlayers) continue;
            out.add(le);
        }
        return out;
    }

    /** Damage with the player as the source without re-triggering the passive effects. */
    private void hurt(LivingEntity le, Player p, double dmg) {
        inAbility = true;
        try {
            le.damage(dmg, p);
        } finally {
            inAbility = false;
        }
    }

    private void flameBurst(Player p, MaceManager.MaceType t, List<LivingEntity> targets) {
        Location c = p.getLocation();
        World w = c.getWorld();
        double r = t.abilityRadius();
        w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        for (int i = 0; i < 48; i++) {
            double ang = 2 * Math.PI * i / 48;
            for (double d = 1; d <= r; d += Math.max(1, r / 3)) {
                w.spawnParticle(Particle.FLAME, c.getX() + Math.cos(ang) * d, c.getY() + 0.3,
                        c.getZ() + Math.sin(ang) * d, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        for (LivingEntity le : targets) {
            le.setFireTicks(120);
            hurt(le, p, t.abilityDamage());
            Vector away = le.getLocation().toVector().subtract(c.toVector());
            away.setY(0);
            if (away.lengthSquared() > 0) away.normalize().multiply(1.0);
            away.setY(0.4);
            le.setVelocity(away);
        }
    }

    private void stormStrike(Player p, MaceManager.MaceType t, List<LivingEntity> targets) {
        World w = p.getWorld();
        int n = 0;
        for (LivingEntity le : targets) {
            if (n++ >= 6) break;
            w.strikeLightningEffect(le.getLocation());
            w.spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
            hurt(le, p, t.abilityDamage());
        }
        // wind gust launches the wielder, slow falling cushions the landing
        p.setVelocity(new Vector(p.getVelocity().getX(), 0.9, p.getVelocity().getZ()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false));
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
    }

    private void voidPull(Player p, MaceManager.MaceType t, List<LivingEntity> targets) {
        Location c = p.getLocation();
        World w = c.getWorld();
        w.playSound(c, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
        w.spawnParticle(Particle.PORTAL, c.clone().add(0, 1, 0), 120, t.abilityRadius() / 3, 1, t.abilityRadius() / 3, 0.5);
        for (LivingEntity le : targets) {
            Vector toward = c.toVector().subtract(le.getLocation().toVector());
            if (toward.lengthSquared() > 0) toward.normalize().multiply(1.2);
            toward.setY(0.3);
            le.setVelocity(toward);
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            hurt(le, p, t.abilityDamage());
        }
    }
}
