package com.holyworld.autoreply.command;

import com.holyworld.autoreply.HolyWorldAutoReply;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public class AICommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("ai")
                    .then(ClientCommandManager.literal("start")
                        .executes(context -> {
                            HolyWorldAutoReply.setEnabled(true);
                            context.getSource().sendFeedback(
                                Text.literal("\u00a7a\u00a7l[AutoReply] \u00a7e\u0410\u0432\u0442\u043e\u043e\u0442\u0432\u0435\u0442\u0447\u0438\u043a \u00a7a\u0412\u041a\u041b\u042e\u0427\u0415\u041d!")
                            );
                            HolyWorldAutoReply.LOGGER.info("[AutoReply] Enabled");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            HolyWorldAutoReply.setEnabled(false);
                            context.getSource().sendFeedback(
                                Text.literal("\u00a7c\u00a7l[AutoReply] \u00a7e\u0410\u0432\u0442\u043e\u043e\u0442\u0432\u0435\u0442\u0447\u0438\u043a \u00a7c\u0412\u042b\u041a\u041b\u042e\u0427\u0415\u041d!")
                            );
                            HolyWorldAutoReply.LOGGER.info("[AutoReply] Disabled");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("status")
                        .executes(context -> {
                            boolean on = HolyWorldAutoReply.isEnabled();
                            String status = on ? "\u00a7aVKL" : "\u00a7cVYKL";
                            context.getSource().sendFeedback(
                                Text.literal("\u00a7b\u00a7l[AutoReply] \u00a7eStatus: " + status)
                            );
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            if (HolyWorldAutoReply.getChatHandler() != null) {
                                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                            }
                            context.getSource().sendFeedback(
                                Text.literal("\u00a7e\u00a7l[AutoReply] \u00a7fAll player states cleared!")
                            );
                            return 1;
                        })
                    )
            );
        });
    }
}
