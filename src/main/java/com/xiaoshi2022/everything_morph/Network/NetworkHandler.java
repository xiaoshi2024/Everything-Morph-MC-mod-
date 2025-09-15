package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EverythingMorphMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static final AtomicInteger PACKET_ID = new AtomicInteger(0);

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, SkinUpdatePacket.class,
                SkinUpdatePacket::encode, SkinUpdatePacket::decode, SkinUpdatePacket::handle);

        LOGGER.info("网络处理器已注册，共注册了 {} 个数据包", id);
    }

    // 更可靠的发送方法，带重试机制（使用配置值）
    public static void sendToAllTrackingWithRetry(Entity entity, Object packet) {
        int maxRetries = com.xiaoshi2022.everything_morph.Config.networkRetryCount;
        int retryDelay = com.xiaoshi2022.everything_morph.Config.networkRetryDelay;

        for (int i = 0; i < maxRetries; i++) {
            try {
                INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
                LOGGER.debug("数据包发送成功，实体ID: {}", entity.getId());
                break;
            } catch (Exception e) {
                LOGGER.warn("数据包发送失败 (尝试 {} / {}): {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    LOGGER.error("数据包发送最终失败", e);
                }
                // 使用配置的延迟时间
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}