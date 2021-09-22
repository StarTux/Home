package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;

public final class UnInviteHomeCommand extends AbstractCommand<HomePlugin> {
    protected UnInviteHomeCommand(final HomePlugin plugin) {
        super(plugin, "uninvitehome");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("<player> [home]")
            .description("Uninvite someone from your home")
            .completers(CommandArgCompleter.NULL, plugin.homesCommand::completeOwnedHomes)
            .playerCaller(plugin.homesCommand::uninvite);
    }
}
