package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SkinUpdatePacket {
    private final int entityId;
    private final ResourceLocation skinTexture; // 改为直接使用 ResourceLocation

    public SkinUpdatePacket(int entityId, ResourceLocation skinTexture) {
        this.entityId = entityId;
        this.skinTexture = skinTexture;
    }

    public static void encode(SkinUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeResourceLocation(packet.skinTexture); // 使用 writeResourceLocation
    }

    public static SkinUpdatePacket decode(FriendlyByteBuf buffer) {
        return new SkinUpdatePacket(buffer.readInt(), buffer.readResourceLocation()); // 使用 readResourceLocation
    }

    public static void handle(SkinUpdatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            // 在客户端主线程处理
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Entity entity = minecraft.level.getEntity(packet.entityId);
                if (entity instanceof WeaponMorphEntity morphEntity) {
                    morphEntity.setSkinTexture(packet.skinTexture); // 直接设置 ResourceLocation
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}