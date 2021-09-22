package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;

public final class HomeCommand extends AbstractCommand<HomePlugin> {
    protected HomeCommand(final HomePlugin plugin) {
        super(plugin, "home");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("[home]")
            .description("Visit your home")
            .completers(plugin.homesCommand::completeUsableHomes)
            .playerCaller(plugin.homesCommand::home);
    }
}
