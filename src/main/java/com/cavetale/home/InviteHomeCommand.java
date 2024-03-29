package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;

public final class InviteHomeCommand extends AbstractCommand<HomePlugin> {
    protected InviteHomeCommand(final HomePlugin plugin) {
        super(plugin, "invitehome");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("<player> [home]")
            .description("Invite someone to use your home")
            .completers(CommandArgCompleter.NULL, plugin.homesCommand::completeOwnedHomes)
            .playerCaller(plugin.homesCommand::invite);
    }
}
