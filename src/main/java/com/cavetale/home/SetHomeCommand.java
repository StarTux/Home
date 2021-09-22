package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;

public final class SetHomeCommand extends AbstractCommand<HomePlugin> {
    protected SetHomeCommand(final HomePlugin plugin) {
        super(plugin, "sethome");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("[name]").denyTabCompletion()
            .description("Set a home")
            .denyTabCompletion()
            .playerCaller(plugin.homesCommand::set);
    }
}
