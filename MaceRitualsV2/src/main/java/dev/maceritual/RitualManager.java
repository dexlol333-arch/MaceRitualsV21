package dev.maceritual;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RitualManager {

    public enum State { COUNTDOWN, ACTIVE, ENDING }

    static final class Session {
        final UUID playerId;
        final ArenaManager.Arena arena;
        final MaceManager.MaceType type;
        final Location returnLoc;
        final boolean inPlace;
        final BossBar bar = BossBar.bossBar(Component.text("Ritual"), 1f,
                BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        final Set<UUID> mobs = new HashSet<>();
        State state = State.COUNTDOWN;
        int countdown, countdownMax, secondsLeft, waveIndex = -1, pause = -1, waveTotalMobs = 1, endingTicks;

        Session(UUID playerId, ArenaManager.Arena arena, MaceManager.MaceType type, Location returnLoc, boolean inPlace) {
            this.inPlace = inPlace;
            this.playerId = playerId;
            this.arena = arena;
            this.type = type;
            this.returnLoc = returnLoc;
        }
    }

    private final MaceRituals plugin;
    private final NamespacedKey mobKey;
    private final Map<UUID, Session> byPlayer = new HashMap<>();
    private final Map<String, Session> byArena = new HashMap<>();
    private final Map<UUID, Location> pendingRespawn = new HashMap<>();
    private BukkitTask ticker;

    public RitualManager(MaceRituals plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "ritual_mob");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public NamespacedKey mobKey() { return mobKey; }
    public boolean isInRitual(UUID id) { return byPlayer.containsKey(id); }
    public boolean isBusy(String arenaName) { return byArena.containsKey(arenaName); }
    public Location popRespawn(UUID id) { return pendingRespawn.remove(id); }

    public ArenaManager.Arena findFreeArena() {
        for (ArenaManager.Arena a : plugin.arenas().all()) {
            if (!byArena.containsKey(a.name) && a.location() != null) return a;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------
    /** In-place rituals (default) always can start; arena mode needs a free arena. */
    public boolean canStart() {
        return !cfg().getBoolean("ritual.use-arenas", false) || findFreeArena() != null;
    }

    public boolean start(Player p, MaceManager.MaceType type) {
        if (byPlayer.containsKey(p.getUniqueId())) return false;

        boolean inPlace = !cfg().getBoolean("ritual.use-arenas", false);
        ArenaManager.Arena arena;
        if (inPlace) {
            // the ritual happens right where the player is standing
            arena = new ArenaManager.Arena("ritual-" + p.getUniqueId());
            arena.setLocation(p.getLocation());
            arena.radius = cfg().getDouble("ritual.radius", 15);
        } else {
            arena = findFreeArena();
            if (arena == null) return false;
        }

        Session s = new Session(p.getUniqueId(), arena, type, p.getLocation().clone(), inPlace);
        s.countdown = cfg().getInt("ritual.countdown-seconds", 10);
        s.countdownMax = Math.max(1, s.countdown);
        byPlayer.put(p.getUniqueId(), s);
        byArena.put(arena.name, s);

        if (!inPlace) {
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.teleport(arena.location());
        }
        if (cfg().getBoolean("ritual.heal-on-start", true)) {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) p.setHealth(hp.getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
        p.showBossBar(s.bar);
        s.bar.name(MaceRituals.color("&5&lRITUAL &8» &7Awakening..."));
        s.bar.progress(0f);
        title(p, "&5&lTHE RITUAL BEGINS", "&7Survive every wave to claim your mace");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);

        if (cfg().getBoolean("ritual.announce-start", true)) {
            Location l = p.getLocation();
            String where = cfg().getBoolean("ritual.announce-coords", true)
                    ? " &7at &eX " + l.getBlockX() + " Y " + l.getBlockY() + " Z " + l.getBlockZ()
                      + " &7in &e" + l.getWorld().getName()
                    : "";
            Bukkit.broadcast(MaceRituals.color(plugin.prefix() + "&e" + p.getName()
                    + " &7has begun a &5&lritual" + where + "&7!"));
        }
        if (cfg().getBoolean("ritual.announce-title", true)) {
            Location l = p.getLocation();
            String sub = "&e" + p.getName();
            if (cfg().getBoolean("ritual.announce-coords", true)) {
                sub += " &7at &eX " + l.getBlockX() + " Y " + l.getBlockY() + " Z " + l.getBlockZ();
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                title(online, "&5&l\u2694 RITUAL STARTED \u2694", sub);
                if (!online.equals(p)) online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.5f);
            }
        }

        startTicker();
        return true;
    }

    private void startTicker() {
        if (ticker != null) return;
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (byPlayer.isEmpty()) {
                    cancel();
                    ticker = null;
                    return;
                }
                for (Session s : new ArrayList<>(byPlayer.values())) tick(s);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------
    private void tick(Session s) {
        Player p = Bukkit.getPlayer(s.playerId);
        if (p == null || !p.isOnline()) {
            fail(s, "Ritual abandoned.", false);
            return;
        }
        switch (s.state) {
            case COUNTDOWN -> tickCountdown(s, p);
            case ACTIVE -> tickActive(s, p);
            case ENDING -> {
                ring(s, Particle.END_ROD);
                if (--s.endingTicks <= 0) finishReturn(s);
            }
        }
    }

    private void tickCountdown(Session s, Player p) {
        if (s.countdown > 0) {
            title(p, "&5&l" + s.countdown, "&7The ritual is awakening...");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                    0.5f + (s.countdownMax - s.countdown) * (1.5f / s.countdownMax));
            ring(s, Particle.SOUL_FIRE_FLAME);
            s.bar.name(MaceRituals.color("&5&lRITUAL &8» &7Begins in &e" + s.countdown + "s"));
            s.bar.progress(Math.max(0f, Math.min(1f, s.countdown / (float) s.countdownMax)));
            s.countdown--;
        } else {
            s.state = State.ACTIVE;
            s.secondsLeft = cfg().getInt("ritual.time-limit-seconds", 300);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
            nextWave(s, p);
        }
    }

    private void tickActive(Session s, Player p) {
        if (s.state != State.ACTIVE) return;
        s.secondsLeft--;
        if (s.secondsLeft <= 0) {
            fail(s, "Time ran out. The ritual collapses.", false);
            return;
        }

        Location c = s.arena.location();
        if (c == null) {
            fail(s, "The arena is unavailable.", true);
            return;
        }

        // keep the player inside the arena
        boolean sameWorld = p.getWorld().equals(c.getWorld());
        double dx = p.getLocation().getX() - c.getX();
        double dz = p.getLocation().getZ() - c.getZ();
        if (!sameWorld || Math.hypot(dx, dz) > s.arena.radius) {
            p.teleport(c);
            plugin.msg(p, "&cYou cannot leave the ritual circle!");
        }

        // forget mobs that died or vanished
        s.mobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || e.isDead() || !e.isValid();
        });

        if (s.mobs.isEmpty()) {
            if (s.pause < 0) {
                s.pause = cfg().getInt("ritual.seconds-between-waves", 4);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                if (s.pause > 0) title(p, "&a&lWAVE CLEARED", "&7Next wave incoming...");
            }
            s.pause--;
            if (s.pause <= 0) {
                s.pause = -1;
                nextWave(s, p);
                if (s.state != State.ACTIVE) return;
            }
        }

        ring(s, Particle.SOUL_FIRE_FLAME);
        updateBar(s);
    }

    private int totalWaves() {
        ConfigurationSection w = cfg().getConfigurationSection("ritual.waves");
        return w == null ? 0 : w.getKeys(false).size();
    }

    private void updateBar(Session s) {
        int total = Math.max(1, totalWaves());
        float cleared = 1f - Math.max(0f, Math.min(1f, s.mobs.size() / (float) s.waveTotalMobs));
        float overall = Math.max(0f, Math.min(1f, (s.waveIndex + cleared) / total));
        int pct = Math.round(overall * 100);
        String time = String.format("%d:%02d", s.secondsLeft / 60, s.secondsLeft % 60);
        s.bar.name(MaceRituals.color("&5&lRITUAL &8» &d" + pct + "% &8| &7Wave &e" + (s.waveIndex + 1) + "&7/&e" + total
                + " &8| &7Mobs &c" + s.mobs.size() + " &8| &7Time &e" + time));
        s.bar.progress(overall);
        s.bar.color(overall < 0.34f ? BossBar.Color.PURPLE : overall < 0.67f ? BossBar.Color.PINK : BossBar.Color.GREEN);
    }

    // ------------------------------------------------------------------
    // Waves
    // ------------------------------------------------------------------
    private void nextWave(Session s, Player p) {
        ConfigurationSection waves = cfg().getConfigurationSection("ritual.waves");
        List<String> keys = waves == null ? List.of() : new ArrayList<>(waves.getKeys(false));
        s.waveIndex++;
        if (s.waveIndex >= keys.size()) {
            complete(s, p);
            return;
        }

        Location c = s.arena.location();
        if (c == null) {
            fail(s, "The arena is unavailable.", true);
            return;
        }
        double mult = cfg().getDouble("ritual.health-multiplier", 1.0);
        boolean glow = cfg().getBoolean("ritual.glowing-mobs", true);

        for (String raw : waves.getStringList(keys.get(s.waveIndex))) {
            String[] parts = raw.split(":");
            EntityType et = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (et == null) {
                plugin.getLogger().warning("Unknown entity type in waves: " + parts[0]);
                continue;
            }
            int count = 1;
            if (parts.length > 1) {
                try { count = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { }
            }
            for (int i = 0; i < count; i++) {
                Location spawn = spawnPoint(c, s.arena.radius);
                Entity ent = c.getWorld().spawnEntity(spawn, et);
                if (!(ent instanceof LivingEntity le)) {
                    ent.remove();
                    continue;
                }
                le.setRemoveWhenFarAway(false);
                le.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null && mult != 1.0) {
                    hp.setBaseValue(hp.getBaseValue() * mult);
                    le.setHealth(hp.getValue());
                }
                // stop sunlight from burning the undead and ruining the fight
                le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 900, 0, false, false));
                if (glow) le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 900, 0, false, false));
                if (le instanceof Mob m) m.setTarget(p);
                c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawn, 20, 0.3, 0.6, 0.3, 0.02);
                s.mobs.add(le.getUniqueId());
            }
        }

        s.waveTotalMobs = Math.max(1, s.mobs.size());
        s.pause = -1;
        title(p, "&5&lWAVE " + (s.waveIndex + 1) + "/" + keys.size(), "&c" + s.mobs.size() + " enemies");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.3f);
        updateBar(s);
    }

    private Location spawnPoint(Location c, double radius) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = c.getWorld();
        for (int i = 0; i < 15; i++) {
            double ang = r.nextDouble(Math.PI * 2);
            double dist = radius * (0.4 + r.nextDouble() * 0.4);
            int bx = (int) Math.floor(c.getX() + Math.cos(ang) * dist);
            int bz = (int) Math.floor(c.getZ() + Math.sin(ang) * dist);
            for (int dy = -2; dy <= 3; dy++) {
                int by = c.getBlockY() + dy;
                if (w.getBlockAt(bx, by, bz).isPassable()
                        && w.getBlockAt(bx, by + 1, bz).isPassable()
                        && !w.getBlockAt(bx, by - 1, bz).isPassable()
                        && !w.getBlockAt(bx, by - 1, bz).isLiquid()) {
                    return new Location(w, bx + 0.5, by, bz + 0.5);
                }
            }
        }
        return c.clone();
    }

    private void ring(Session s, Particle particle) {
        Location c = s.arena.location();
        if (c == null) return;
        double r = s.arena.radius;
        int points = (int) Math.min(120, Math.max(24, r * 4));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            c.getWorld().spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY() + 0.3,
                    c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------
    private void complete(Session s, Player p) {
        s.state = State.ENDING;
        s.endingTicks = 6;
        clearMobs(s);
        p.hideBossBar(s.bar);

        ItemStack mace = plugin.maces().buildMace(s.type);
        for (ItemStack left : p.getInventory().addItem(mace).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
        title(p, "&6&lRITUAL COMPLETE", "&7The mace is yours");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 0.6, 1, 0.6, 0.4);
        plugin.msg(p, s.inPlace ? "&aThe ritual is complete." : "&aThe ritual is complete. You are returned shortly.");
        if (cfg().getBoolean("ritual.broadcast-on-success", true)) {
            Bukkit.broadcast(MaceRituals.color(plugin.prefix() + "&e" + p.getName()
                    + " &7completed a ritual and forged " + s.type.maceName() + "&7!"));
        }
    }

    private void fail(Session s, String reason, boolean forceRefund) {
        Player p = Bukkit.getPlayer(s.playerId);
        clearMobs(s);
        if (!forceRefund && cfg().getBoolean("ritual.announce-fail", true)) {
            String n = Bukkit.getOfflinePlayer(s.playerId).getName();
            if (n != null) Bukkit.broadcast(MaceRituals.color(plugin.prefix() + "&e" + n + "&7's ritual has failed."));
        }
        if (p != null) {
            p.hideBossBar(s.bar);
            plugin.msg(p, "&c" + reason);
            if (!p.isDead()) {
                title(p, "&c&lRITUAL FAILED", "&7" + reason);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1f);
                if (forceRefund || cfg().getBoolean("ritual.return-core-on-fail", false)) {
                    for (ItemStack left : p.getInventory().addItem(plugin.maces().buildCore(s.type, 1)).values()) {
                        p.getWorld().dropItem(p.getLocation(), left);
                    }
                    plugin.msg(p, "&7Your heavy core was returned.");
                }
                if (!s.inPlace) p.teleport(s.returnLoc);
            } else if (!s.inPlace && cfg().getBoolean("ritual.respawn-at-return", true)) {
                pendingRespawn.put(p.getUniqueId(), s.returnLoc);
            }
        }
        cleanup(s);
    }

    private void finishReturn(Session s) {
        Player p = Bukkit.getPlayer(s.playerId);
        if (p != null && p.isOnline() && !p.isDead() && !s.inPlace) p.teleport(s.returnLoc);
        cleanup(s);
    }

    private void cleanup(Session s) {
        byPlayer.remove(s.playerId);
        byArena.remove(s.arena.name, s);
        clearMobs(s);
    }

    private void clearMobs(Session s) {
        for (UUID id : s.mobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        s.mobs.clear();
    }

    // ------------------------------------------------------------------
    // Public hooks used by listeners/commands
    // ------------------------------------------------------------------
    /** Player died or quit mid-ritual. */
    public void playerLost(Player p, String reason) {
        Session s = byPlayer.get(p.getUniqueId());
        if (s == null) return;
        if (s.state == State.ENDING) finishReturn(s);
        else fail(s, reason, false);
    }

    /** Admin stop: always refunds the core. */
    public boolean adminStop(Player p) {
        Session s = byPlayer.get(p.getUniqueId());
        if (s == null) return false;
        if (s.state == State.ENDING) finishReturn(s);
        else fail(s, "The ritual was stopped by an admin.", true);
        return true;
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(byPlayer.values())) {
            if (s.state == State.ENDING) finishReturn(s);
            else fail(s, "The server is restarting.", true);
        }
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void title(Player p, String t, String sub) {
        p.showTitle(Title.title(MaceRituals.color(t), MaceRituals.color(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
    }
}
