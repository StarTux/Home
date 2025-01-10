package com.cavetale.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Util {
    private Util() { }

    public static Component frame(Component text, TextColor frameColor) {
        return Component.text().color(frameColor)
            .append(Component.text("            ", null, TextDecoration.STRIKETHROUGH))
            .append(Component.text("[ "))
            .append(text)
            .append(Component.text(" ]"))
            .append(Component.text("            ", null, TextDecoration.STRIKETHROUGH))
            .build();
    }

    public static Component frame(String text) {
        return frame(Component.text(text, NamedTextColor.WHITE), NamedTextColor.BLUE)
            .asComponent();
    }
}
