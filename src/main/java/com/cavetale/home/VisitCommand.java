package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;

public final class VisitCommand extends AbstractCommand<HomePlugin> {
    protected VisitCommand(final HomePlugin plugin) {
        super(plugin, "visit");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Visit public homes")
            .completers(plugin.homesCommand::completePublicHomes)
            .remotePlayerCaller(plugin.homesCommand::visit);
    }
}
