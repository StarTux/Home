package com.cavetale.home.util;

import net.md_5.bungee.api.chat.ComponentBuilder;

public final class Msg {
    private Msg() { }

    public static ComponentBuilder builder(String txt) {
        return new ComponentBuilder(txt);
    }
}
