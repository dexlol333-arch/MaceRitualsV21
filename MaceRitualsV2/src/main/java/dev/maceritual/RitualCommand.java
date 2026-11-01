package dev.maceritual;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RitualCommand implements CommandExecutor, TabCompleter {

    private final MaceRituals plugin;

    public RitualCommand(MaceRituals plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("maceritual.admin")) {
            plugin.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "arena" -> arena(sender, args);
            case "givecore" -> give(sender, args, true);
            case "givemace" -> give(sender, args, false);
            case "types" -> {
                plugin.msg(sender, "&7Mace types:");
                for (MaceManager.MaceType t : plugin.maces().all()) {
                    sender.sendMessage(MaceRituals.color("  &e" + t.id() + " &8- " + t.maceName() + " &8(weight " + t.weight() + ")"));
                }
            }
            case "stop" -> {
                if (args.length < 2) { plugin.msg(sender, "&cUsage: /ritual stop <player>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null || !plugin.rituals().adminStop(t)) {
                    plugin.msg(sender, "&cThat player is not in a ritual.");
                } else {
                    plugin.msg(sender, "&aStopped the ritual and refunded the core.");
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg(sender, "&aConfig and arenas reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(MaceRituals.color("&8&m----------------------------------------"));
        s.sendMessage(MaceRituals.color("&5&lMACE RITUALS &7admin commands"));
        s.sendMessage(MaceRituals.color("&e/ritual arena create <name> [radius] &7- Make an arena at your spot"));
        s.sendMessage(MaceRituals.color("&e/ritual arena setcenter <name> &7- Move the arena center to you"));
        s.sendMessage(MaceRituals.color("&e/ritual arena setradius <name> <radius> &7- Change the arena size"));
        s.sendMessage(MaceRituals.color("&e/ritual arena delete <name> &7- Delete an arena"));
        s.sendMessage(MaceRituals.color("&e/ritual arena tp <name> &7- Teleport to an arena"));
        s.sendMessage(MaceRituals.color("&e/ritual arena list &7- List arenas and status"));
        s.sendMessage(MaceRituals.color("&e/ritual givecore <player> <type> [amount] &7- Give a custom core"));
        s.sendMessage(MaceRituals.color("&e/ritual givemace <player> <type> &7- Give a finished mace"));
        s.sendMessage(MaceRituals.color("&e/ritual types &7- List mace types"));
        s.sendMessage(MaceRituals.color("&e/ritual stop <player> &7- End a ritual (refunds core)"));
        s.sendMessage(MaceRituals.color("&e/ritual reload &7- Reload config and arenas"));
        s.sendMessage(MaceRituals.color("&8&m----------------------------------------"));
    }

    private void arena(CommandSender s, String[] a) {
        if (a.length < 2) { help(s); return; }
        ArenaManager am = plugin.arenas();
        String sub = a[1].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            if (am.all().isEmpty()) { plugin.msg(s, "&7No arenas set up. Use &e/ritual arena create <name>"); return; }
            for (ArenaManager.Arena ar : am.all()) {
                String status = plugin.rituals().isBusy(ar.name) ? "&cIn use" : "&aFree";
                String world = ar.location() == null ? " &c(world not loaded)" : "";
                s.sendMessage(MaceRituals.color("  &e" + ar.name + " &8- " + status + " &8| &7r=" + ar.radius
                        + " &8| &7" + ar.world + " " + (int) ar.x + "," + (int) ar.y + "," + (int) ar.z + world));
            }
            return;
        }
        if (a.length < 3) { plugin.msg(s, "&cUsage: /ritual arena " + sub + " <name>"); return; }
        String name = a[2].toLowerCase(Locale.ROOT);
        ArenaManager.Arena ar = am.get(name);

        switch (sub) {
            case "create" -> {
                if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
                if (ar != null) { plugin.msg(s, "&cThat arena already exists."); return; }
                double r = plugin.getConfig().getDouble("arena.default-radius", 20);
                if (a.length > 3) {
                    try { r = Double.parseDouble(a[3]); } catch (NumberFormatException e) { plugin.msg(s, "&cInvalid radius."); return; }
                }
                am.create(name, p.getLocation(), r);
                plugin.msg(s, "&aArena &e" + name + " &acreated here with radius &e" + r + "&a.");
            }
            case "setcenter" -> {
                if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
                if (ar == null) { plugin.msg(s, "&cUnknown arena."); return; }
                ar.setLocation(p.getLocation());
                am.save();
                plugin.msg(s, "&aArena center moved.");
            }
            case "setradius" -> {
                if (ar == null) { plugin.msg(s, "&cUnknown arena."); return; }
                if (a.length < 4) { plugin.msg(s, "&cUsage: /ritual arena setradius <name> <radius>"); return; }
                try { ar.radius = Double.parseDouble(a[3]); } catch (NumberFormatException e) { plugin.msg(s, "&cInvalid radius."); return; }
                am.save();
                plugin.msg(s, "&aRadius set to &e" + ar.radius + "&a.");
            }
            case "delete" -> {
                if (ar == null) { plugin.msg(s, "&cUnknown arena."); return; }
                if (plugin.rituals().isBusy(name)) { plugin.msg(s, "&cA ritual is running there right now."); return; }
                am.delete(name);
                plugin.msg(s, "&aArena deleted.");
            }
            case "tp" -> {
                if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
                if (ar == null || ar.location() == null) { plugin.msg(s, "&cUnknown arena or world not loaded."); return; }
                p.teleport(ar.location());
            }
            default -> help(s);
        }
    }

    private void give(CommandSender s, String[] a, boolean core) {
        if (a.length < 3) {
            plugin.msg(s, "&cUsage: /ritual " + (core ? "givecore <player> <type> [amount]" : "givemace <player> <type>"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { plugin.msg(s, "&cThat player is not online."); return; }
        MaceManager.MaceType type = plugin.maces().get(a[2]);
        if (type == null) { plugin.msg(s, "&cUnknown type. Use &e/ritual types"); return; }
        int amount = 1;
        if (core && a.length > 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { }
        }
        ItemStack item = core ? plugin.maces().buildCore(type, amount) : plugin.maces().buildMace(type);
        for (ItemStack left : t.getInventory().addItem(item).values()) {
            t.getWorld().dropItem(t.getLocation(), left);
        }
        plugin.msg(s, "&aGave &e" + t.getName() + " &athe " + (core ? "core" : "mace") + " &e" + type.id() + "&a.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("maceritual.admin")) return out;

        if (args.length == 1) {
            out.addAll(List.of("arena", "givecore", "givemace", "types", "stop", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "arena" -> out.addAll(List.of("create", "setcenter", "setradius", "delete", "tp", "list"));
                case "givecore", "givemace", "stop" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> { }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "arena" -> {
                    if (!args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                        plugin.arenas().all().forEach(ar -> out.add(ar.name));
                    }
                }
                case "givecore", "givemace" -> plugin.maces().all().forEach(t -> out.add(t.id()));
                default -> { }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
