package dev.maceritual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaceRituals extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MaceManager maces;
    private ArenaManager arenas;
    private RitualManager rituals;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maces = new MaceManager(this);
        arenas = new ArenaManager(this);
        rituals = new RitualManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new VaultListener(this), this);
        pm.registerEvents(new CraftListener(this), this);
        pm.registerEvents(new RitualListener(this), this);
        pm.registerEvents(new AbilityListener(this), this);

        RitualCommand cmd = new RitualCommand(this);
        getCommand("ritual").setExecutor(cmd);
        getCommand("ritual").setTabCompleter(cmd);
        getLogger().info("MaceRituals enabled with " + maces.all().size() + " mace types and "
                + arenas.all().size() + " arenas.");
    }

    @Override
    public void onDisable() {
        if (rituals != null) rituals.shutdown();
        if (arenas != null) arenas.save();
    }

    public void reloadAll() {
        reloadConfig();
        maces.load();
        arenas.load();
    }

    public MaceManager maces() { return maces; }
    public ArenaManager arenas() { return arenas; }
    public RitualManager rituals() { return rituals; }

    /** Converts &-coded text to a non-italic component. */
    public static Component color(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public String prefix() {
        return getConfig().getString("messages.prefix", "&5&lRITUAL &8» ");
    }

    public void msg(CommandSender to, String s) {
        to.sendMessage(LEGACY.deserialize(prefix() + s));
    }
}
