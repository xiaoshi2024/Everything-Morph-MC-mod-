package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

import static com.mojang.text2speech.Narrator.LOGGER;

// 在 SkinUpdatePacket.java 中添加皮肤状态字段
public class SkinUpdatePacket {
    private final int entityId;
    private final ResourceLocation skinTexture;
    private final int skinState; // 0=NOT_LOADED, 1=LOADING, 2=LOADED, 3=FAILED

    public SkinUpdatePacket(int entityId, ResourceLocation skinTexture, int skinState) {
        this.entityId = entityId;
        this.skinTexture = skinTexture != null ? skinTexture : new ResourceLocation("textures/entity/steve.png");
        this.skinState = skinState;
    }

    // 修改编码解码方法，包含skinState
    public static void encode(SkinUpdatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.entityId);
        buffer.writeResourceLocation(msg.skinTexture);
        buffer.writeInt(msg.skinState);
    }

    public static SkinUpdatePacket decode(FriendlyByteBuf buffer) {
        return new SkinUpdatePacket(buffer.readInt(), buffer.readResourceLocation(), buffer.readInt());
    }

    // 修改处理逻辑
// 修改 SkinUpdatePacket 的处理逻辑
    public static void handle(SkinUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LOGGER.info("收到皮肤更新包: 实体 {} -> 皮肤 {}", msg.entityId, msg.skinTexture);
            // 这是在服务端执行的代码！
            if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                // 服务端收到客户端发来的皮肤状态更新
                var server = ctx.get().getSender().getServer();
                if (server != null) {
                    // 在所有维度中查找实体
                    for (var level : server.getAllLevels()) {
                        Entity entity = level.getEntity(msg.entityId);
                        if (entity instanceof WeaponMorphEntity morph) {
                            // 在服务端更新皮肤状态
                            morph.setSkinTexture(msg.skinTexture);
                            morph.skinLoadState = WeaponMorphEntity.SkinLoadState.values()[msg.skinState];
                            morph.skinLoadedFromUUID = (msg.skinState == WeaponMorphEntity.SkinLoadState.LOADED.ordinal());

                            LOGGER.info("✅ 服务端收到皮肤状态更新: 实体 {} 状态 {}",
                                    msg.entityId, morph.skinLoadState);
                            break;
                        }
                    }
                }
            } else {
                // 这是在客户端执行的代码（不应该执行到这里）
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) {
                    Entity entity = level.getEntity(msg.entityId);
                    if (entity instanceof WeaponMorphEntity morph) {
                        morph.setSkinTexture(msg.skinTexture);
                        morph.setSkinLoadState(WeaponMorphEntity.SkinLoadState.values()[msg.skinState]);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}