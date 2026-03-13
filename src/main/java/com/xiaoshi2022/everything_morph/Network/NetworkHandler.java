package com.xiaoshi2022.everything_morph.Network;

import com.xiaoshi2022.everything_morph.Config;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EverythingMorphMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        // 注册皮肤更新包
        INSTANCE.registerMessage(packetId++, SkinUpdatePacket.class,
                SkinUpdatePacket::encode, SkinUpdatePacket::decode, SkinUpdatePacket::handle);

        // 注册召唤数据包
        INSTANCE.registerMessage(packetId++, SummonMorphPacket.class,
                SummonMorphPacket::encode, SummonMorphPacket::decode, SummonMorphPacket::handle);

        LOGGER.info("网络处理器已注册，共注册了 {} 个数据包", packetId);
    }

    // 发送到服务器（带配置的重试机制）- 给客户端使用
    public static void sendToServer(Object packet) {
        // 从配置获取重试设置
        int maxRetries = Config.networkRetryCount;
        int retryDelay = Config.networkRetryDelay;

        for (int i = 0; i < maxRetries; i++) {
            try {
                INSTANCE.sendToServer(packet);
                LOGGER.debug("数据包发送到服务器成功");
                return;
            } catch (Exception e) {
                LOGGER.warn("数据包发送到服务器失败 (尝试 {} / {}): {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    LOGGER.error("数据包发送到服务器最终失败", e);
                } else {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // 发送到所有追踪实体的玩家（带配置的重试机制）
    public static void sendToAllTracking(Entity entity, Object packet) {
        if (entity == null) return;

        int maxRetries = Config.networkRetryCount;
        int retryDelay = Config.networkRetryDelay;

        for (int i = 0; i < maxRetries; i++) {
            try {
                INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
                LOGGER.debug("数据包发送成功到追踪实体的玩家，实体ID: {}", entity.getId());
                return;
            } catch (Exception e) {
                LOGGER.warn("数据包发送失败 (尝试 {} / {}): {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    LOGGER.error("数据包发送最终失败，实体ID: {}", entity.getId(), e);
                } else {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // 发送到指定玩家（带配置的重试机制）
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (player == null) return;

        int maxRetries = Config.networkRetryCount;
        int retryDelay = Config.networkRetryDelay;

        for (int i = 0; i < maxRetries; i++) {
            try {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
                LOGGER.debug("数据包发送成功到玩家: {}", player.getName().getString());
                return;
            } catch (Exception e) {
                LOGGER.warn("数据包发送到玩家失败 (尝试 {} / {}): {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    LOGGER.error("数据包发送到玩家最终失败，玩家: {}", player.getName().getString(), e);
                } else {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // 发送到所有玩家（带配置的重试机制）
    public static void sendToAll(Object packet) {
        int maxRetries = Config.networkRetryCount;
        int retryDelay = Config.networkRetryDelay;

        for (int i = 0; i < maxRetries; i++) {
            try {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
                LOGGER.debug("数据包发送成功到所有玩家");
                return;
            } catch (Exception e) {
                LOGGER.warn("数据包发送到所有玩家失败 (尝试 {} / {}): {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    LOGGER.error("数据包发送到所有玩家最终失败", e);
                } else {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // 无重试的简单发送方法（用于不需要可靠性的场合）
    public static void sendToServerNoRetry(Object packet) {
        try {
            INSTANCE.sendToServer(packet);
        } catch (Exception e) {
            LOGGER.error("发送数据包到服务器失败", e);
        }
    }

    public static void sendToAllTrackingNoRetry(Entity entity, Object packet) {
        try {
            INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
        } catch (Exception e) {
            LOGGER.error("发送数据包到追踪实体的玩家失败，实体ID: {}", entity.getId(), e);
        }
    }
}