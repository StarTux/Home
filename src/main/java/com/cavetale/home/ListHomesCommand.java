package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;

public final class ListHomesCommand extends AbstractCommand<HomePlugin> {
    protected ListHomesCommand(final HomePlugin plugin) {
        super(plugin, "listhomes");
    }

    @Override
    protected void onEnable() {
        rootNode.denyTabCompletion()
            .description("List your homes")
            .playerCaller(plugin.homesCommand::list);
    }
}
