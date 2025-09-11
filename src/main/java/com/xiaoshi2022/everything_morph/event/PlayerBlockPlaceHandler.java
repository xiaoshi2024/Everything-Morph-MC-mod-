// 在 com.xiaoshi2022.everything_morph.event 包中创建 PlayerBlockPlaceHandler.java
package com.xiaoshi2022.everything_morph.event;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.xiaoshi2022.everything_morph.EverythingMorphMod.LOGGER;

@Mod.EventBusSubscriber(modid = "everything_morph") // 替换为您的mod id
public class PlayerBlockPlaceHandler {

    // 在 PlayerBlockPlaceHandler.java 中
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !event.getLevel().isClientSide()) {
            LevelAccessor levelAccessor = event.getLevel();
            if (levelAccessor instanceof Level level) {
                BlockPos placedPos = event.getPos();

                LOGGER.info("玩家 {} 在位置 {} 放置方块", player.getName().getString(), placedPos);

                // 查找附近的 WeaponMorphEntity - 放宽筛选条件
                List<WeaponMorphEntity> morphEntities = level.getEntitiesOfClass(
                        WeaponMorphEntity.class,
                        new AABB(placedPos).inflate(32.0), // 32格范围内
                        entity -> {
                            // 调试信息：检查每个实体的状态
                            LOGGER.info("检查实体 {}: 主人={}, 有主人={}, 物品类型={}, 方块数量={}",
                                    entity.getId(),
                                    entity.getOwner() != null ? entity.getOwner().getName().getString() : "null",
                                    entity.getOwner() != null,
                                    entity.getOriginalItem().getItem().getClass().getSimpleName(),
                                    entity.getBlockCount());

                            // 主要条件：主人是当前玩家
                            LivingEntity owner = entity.getOwner();
                            boolean isOwnedByPlayer = owner != null && owner == player;

                            // 次要条件：有方块物品（但不强制要求有剩余方块，因为可能刚生成）
                            boolean hasBlockItem = entity.getOriginalItem().getItem() instanceof BlockItem;

                            return isOwnedByPlayer && hasBlockItem;
                        }
                );

                LOGGER.info("玩家 {} 放置方块，找到 {} 个可跟随的实体", player.getName().getString(), morphEntities.size());

                // 通知所有找到的实体
                for (WeaponMorphEntity entity : morphEntities) {
                    LOGGER.info("通知实体 {} 跟随放置方块，当前位置: {}", entity.getId(), entity.blockPosition());
                    entity.recordPlayerPlacement(placedPos);
                }
            }
        }
    }
}