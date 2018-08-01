package com.cavetale.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.json.simple.JSONValue;

final class Msg {
    private Msg() { }

    static void consoleCommand(String cmd, Object... args) {
        if (args.length > 0) cmd = String.format(cmd, args);
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
    }

    static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    static void msg(Player player, ChatColor color, String msg, Object... args) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(msg, args));
        map.put("color", color.name().toLowerCase());
        raw(player, map);
    }

    static void title(Player player, String title, String subtitle) {
        player.resetTitle();
        player.sendTitle(format(title), format(subtitle), -1, -1, -1);
    }

    static void actionBar(Player player, String text, Object... args) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(text, args));
        consoleCommand("minecraft:title %s actionbar %s", player.getName(), JSONValue.toJSONString(map));
    }

    static void raw(Player player, Object... obj) {
        if (obj.length == 0) return;
        if (obj.length == 1) {
            consoleCommand("minecraft:tellraw %s %s", player.getName(), JSONValue.toJSONString(obj[0]));
        } else {
            consoleCommand("minecraft:tellraw %s %s", player.getName(), JSONValue.toJSONString(Arrays.asList(obj)));
        }
    }

    static Object button(ChatColor color, String chat, String command, String tooltip) {
        Map<String, Object> map = new HashMap<>();
        if (color != null) {
            map.put("color", color.name().toLowerCase());
        }
        map.put("text", format(chat));
        if (command != null) {
            Map<String, Object> clickEvent = new HashMap<>();
            map.put("clickEvent", clickEvent);
            if (command.endsWith(" ")) {
                clickEvent.put("action", "suggest_command");
            } else {
                clickEvent.put("action", "run_command");
            }
            clickEvent.put("value", command);
            map.put("insertion", command);
        }
        List<String> lines = new ArrayList<>();
        if (tooltip != null) {
            Map<String, Object> hoverEvent = new HashMap<>();
            map.put("hoverEvent", hoverEvent);
            hoverEvent.put("action", "show_text");
            hoverEvent.put("value", tooltip);
        }
        return map;
    }

    public static String toJSONString(Object o) {
        return JSONValue.toJSONString(o);
    }

    public static Object fromJSONString(String s) {
        return JSONValue.parse(s);
    }
}
