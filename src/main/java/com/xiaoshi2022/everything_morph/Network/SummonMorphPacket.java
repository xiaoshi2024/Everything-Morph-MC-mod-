package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.event.MorphItemEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SummonMorphPacket {
    private final ItemStack stack;
    private final boolean hasMorph;
    private final boolean hasFlySword;

    public SummonMorphPacket(ItemStack stack, boolean hasMorph, boolean hasFlySword) {
        this.stack = stack;
        this.hasMorph = hasMorph;
        this.hasFlySword = hasFlySword;
    }

    public static void encode(SummonMorphPacket packet, FriendlyByteBuf buffer) {
        buffer.writeItem(packet.stack);
        buffer.writeBoolean(packet.hasMorph);
        buffer.writeBoolean(packet.hasFlySword);
    }

    public static SummonMorphPacket decode(FriendlyByteBuf buffer) {
        return new SummonMorphPacket(
                buffer.readItem(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    public static void handle(SummonMorphPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                EverythingMorphMod.LOGGER.info("服务器收到召唤包，玩家: {}", player.getName().getString());
                MorphItemEventHandler.handleSummonPacket(player, packet.stack, packet.hasMorph, packet.hasFlySword);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}