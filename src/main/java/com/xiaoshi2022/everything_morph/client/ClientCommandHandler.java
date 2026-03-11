package com.xiaoshi2022.everything_morph.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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

        // 测试皮肤命令 - 使用 CSLIntegration
        dispatcher.register(Commands.literal("testskin")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            Minecraft minecraft = Minecraft.getInstance();

                            if (minecraft.player != null) {
                                ResourceLocation skin = null;
                                String source = "未知";

                                if (CSLIntegration.isAvailable()) {
                                    skin = CSLIntegration.getSkin(name);
                                    source = "CustomSkinLoader";
                                }

                                minecraft.player.sendSystemMessage(
                                        Component.literal("§e===== 皮肤查询结果 =====")
                                );
                                minecraft.player.sendSystemMessage(
                                        Component.literal("§e查询名称: §f" + name)
                                );
                                minecraft.player.sendSystemMessage(
                                        Component.literal("§e皮肤来源: §f" + source)
                                );
                                minecraft.player.sendSystemMessage(
                                        Component.literal("§e匹配结果: §f" + (skin != null ? skin : "未找到"))
                                );

                                if (skin != null) {
                                    minecraft.player.sendSystemMessage(
                                            Component.literal("§a✅ 找到皮肤!")
                                    );
                                } else {
                                    minecraft.player.sendSystemMessage(
                                            Component.literal("§c❌ 未找到皮肤，将使用默认皮肤")
                                    );
                                }
                            }
                            return 1;
                        }))
        );

        // 检查 CSL 状态
        dispatcher.register(Commands.literal("cslstatus")
                .executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        boolean available = CSLIntegration.isAvailable();
                        minecraft.player.sendSystemMessage(
                                Component.literal("§6===== CustomSkinLoader 状态 =====")
                        );
                        minecraft.player.sendSystemMessage(
                                Component.literal("§e已安装: §f" + (available ? "✅ 是" : "❌ 否"))
                        );
                        if (!available) {
                            minecraft.player.sendSystemMessage(
                                    Component.literal("§7提示: 安装 CustomSkinLoader 可获得更多皮肤")
                            );
                        }
                    }
                    return 1;
                })
        );
    }
}