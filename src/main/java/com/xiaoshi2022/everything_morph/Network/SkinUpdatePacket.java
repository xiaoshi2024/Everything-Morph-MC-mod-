package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class SkinUpdatePacket {
    private static final Logger LOGGER = LogManager.getLogger();
    private final int entityId;
    private final ResourceLocation skinTexture;
    private final long timestamp; // 添加时间戳用于验证

    public SkinUpdatePacket(int entityId, ResourceLocation skinTexture) {
        this.entityId = entityId;
        this.skinTexture = skinTexture;
        this.timestamp = System.currentTimeMillis();
    }

    public static void encode(SkinUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeResourceLocation(packet.skinTexture);
        buffer.writeLong(packet.timestamp); // 编码时间戳
        LOGGER.debug("编码皮肤包: entityId={}, skin={}, timestamp={}",
                packet.entityId, packet.skinTexture, packet.timestamp);
    }

    public static SkinUpdatePacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();
        ResourceLocation skin = buffer.readResourceLocation();
        long timestamp = buffer.readLong();
        LOGGER.debug("解码皮肤包: entityId={}, skin={}, timestamp={}", entityId, skin, timestamp);
        return new SkinUpdatePacket(entityId, skin);
    }

    public static void handle(SkinUpdatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Entity entity = minecraft.level.getEntity(packet.entityId);
                if (entity instanceof WeaponMorphEntity morphEntity) {
                    // 检查时间戳，避免处理旧的包
                    if (System.currentTimeMillis() - packet.getTimestamp() > 5000) {
                        LOGGER.warn("收到过时的皮肤包，忽略");
                        return;
                    }

                    LOGGER.info("客户端收到皮肤更新: entityId={}, skin={}",
                            packet.entityId, packet.skinTexture);
                    morphEntity.setSkinTexture(packet.skinTexture);
                    morphEntity.skinLoadState = WeaponMorphEntity.SkinLoadState.LOADED;
                } else {
                    LOGGER.warn("找不到实体: {}", packet.entityId);
                }
            }
        });

        context.get().setPacketHandled(true);
    }

    public long getTimestamp() {
        return timestamp;
    }
}