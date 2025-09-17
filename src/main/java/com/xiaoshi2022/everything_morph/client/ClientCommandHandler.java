package com.xiaoshi2022.everything_morph.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "everything_morph", value = Dist.CLIENT)
public class ClientCommandHandler {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("debugskin")
                .executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        int skinCount = ResourcePackSkinLoader.getInstance().getAvailableSkinCount();
                        minecraft.player.sendSystemMessage(
                                Component.literal("找到 " + skinCount + " 个皮肤文件")
                        );

                        for (ResourceLocation skin : ResourcePackSkinLoader.getInstance().getAllSkins()) {
                            minecraft.player.sendSystemMessage(
                                    Component.literal("皮肤: " + skin.toString())
                            );
                        }
                    }
                    return 1;
                }));

        dispatcher.register(Commands.literal("everythingmorph")
                .then(Commands.literal("reloadskins")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ResourcePackSkinLoader.getInstance().reloadExternalSkins();
                            context.getSource().sendSuccess(() ->
                                    Component.literal("成功重新加载外部皮肤"), false);
                            return 1;
                        })
                )
        );

    }
}