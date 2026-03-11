package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class SkinUpdatePacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final int entityId;
    private final ResourceLocation skinTexture;
    private final int skinState; // 0=NOT_LOADED, 1=LOADING, 2=LOADED, 3=FAILED

    public SkinUpdatePacket(int entityId, ResourceLocation skinTexture, int skinState) {
        this.entityId = entityId;
        // ✅ 修复：允许为 null，表示皮肤尚未加载
        this.skinTexture = skinTexture;
        this.skinState = skinState;
    }

    public static void encode(SkinUpdatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.entityId);

        // ✅ 修复：写入一个标志位表示是否为 null
        boolean hasTexture = msg.skinTexture != null;
        buffer.writeBoolean(hasTexture);
        if (hasTexture) {
            buffer.writeResourceLocation(msg.skinTexture);
        }

        buffer.writeInt(msg.skinState);
    }

    public static SkinUpdatePacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();

        // ✅ 修复：根据标志位读取
        boolean hasTexture = buffer.readBoolean();
        ResourceLocation skinTexture = hasTexture ? buffer.readResourceLocation() : null;

        int skinState = buffer.readInt();

        return new SkinUpdatePacket(entityId, skinTexture, skinState);
    }

    public static void handle(SkinUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                // 服务端处理
                var server = ctx.get().getSender().getServer();
                if (server != null) {
                    for (var level : server.getAllLevels()) {
                        Entity entity = level.getEntity(msg.entityId);
                        if (entity instanceof WeaponMorphEntity morph) {
                            // ✅ 修复：如果皮肤为 null，表示尚未加载
                            if (msg.skinTexture != null) {
                                morph.setSkinTexture(msg.skinTexture);
                            }
                            morph.skinLoadState = WeaponMorphEntity.SkinLoadState.values()[msg.skinState];
                            morph.skinLoadedFromUUID = (msg.skinState == WeaponMorphEntity.SkinLoadState.LOADED.ordinal());

                            LOGGER.info("✅ 服务端收到皮肤状态更新: 实体 {} 状态 {}, 皮肤: {}",
                                    msg.entityId, morph.skinLoadState, msg.skinTexture);
                            break;
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}