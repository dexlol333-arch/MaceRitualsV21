package dev.maceritual;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ArenaManager {

    public static final class Arena {
        public final String name;
        public String world;
        public double x, y, z, radius;
        public float yaw, pitch;

        Arena(String name) { this.name = name; }

        public void setLocation(Location l) {
            this.world = l.getWorld().getName();
            this.x = l.getX();
            this.y = l.getY();
            this.z = l.getZ();
            this.yaw = l.getYaw();
            this.pitch = l.getPitch();
        }

        /** Returns null if the arena's world is not loaded. */
        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final MaceRituals plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(MaceRituals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String name : y.getKeys(false)) {
            Arena a = new Arena(name);
            a.world = y.getString(name + ".world", "world");
            a.x = y.getDouble(name + ".x");
            a.y = y.getDouble(name + ".y");
            a.z = y.getDouble(name + ".z");
            a.yaw = (float) y.getDouble(name + ".yaw");
            a.pitch = (float) y.getDouble(name + ".pitch");
            a.radius = y.getDouble(name + ".radius", 20);
            arenas.put(name, a);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            y.set(a.name + ".world", a.world);
            y.set(a.name + ".x", a.x);
            y.set(a.name + ".y", a.y);
            y.set(a.name + ".z", a.z);
            y.set(a.name + ".yaw", a.yaw);
            y.set(a.name + ".pitch", a.pitch);
            y.set(a.name + ".radius", a.radius);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save arenas.yml: " + e.getMessage());
        }
    }

    public Arena get(String name) { return arenas.get(name.toLowerCase()); }
    public Collection<Arena> all() { return arenas.values(); }

    public Arena create(String name, Location l, double radius) {
        Arena a = new Arena(name.toLowerCase());
        a.setLocation(l);
        a.radius = radius;
        arenas.put(a.name, a);
        save();
        return a;
    }

    public void delete(String name) {
        arenas.remove(name.toLowerCase());
        save();
    }
}
