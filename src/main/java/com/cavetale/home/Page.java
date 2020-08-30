package com.cavetale.home;

import java.util.ArrayList;
import java.util.List;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public final class Page {
    final List<TextComponent> lines;

    public Page() {
        lines = new ArrayList<>(10);
    }

    public Page(final List<TextComponent> lines) {
        this.lines = lines;
    }

    public void send(Player player) {
        for (TextComponent line : lines) {
            player.sendMessage(line);
        }
    }

    public void addLine(TextComponent line) {
        lines.add(line);
    }

    public int lineCount() {
        return lines.size();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
