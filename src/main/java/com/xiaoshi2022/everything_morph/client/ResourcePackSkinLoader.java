package com.xiaoshi2022.everything_morph.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 资源包皮肤加载器 - 从资源包中随机选择皮肤文件
 */
@OnlyIn(Dist.CLIENT)
public class ResourcePackSkinLoader implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourcePackSkinLoader INSTANCE = new ResourcePackSkinLoader();
    private static final Random RANDOM = new Random();
    private static final String MOD_ID = "everything_morph";
    private static final String SKIN_FOLDER = "textures/entity/skins/"; // 修正路径

    private final List<ResourceLocation> availableSkins = new CopyOnWriteArrayList<>();
    private boolean isInitialized = false;

    private ResourcePackSkinLoader() {
        // 私有构造函数，使用单例模式
    }

    public static ResourcePackSkinLoader getInstance() {
        return INSTANCE;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // 在资源管理器重载时重新加载皮肤列表
        reloadSkins(resourceManager);
    }

    private void reloadSkins(ResourceManager resourceManager) {
        availableSkins.clear();

        // 查找所有皮肤文件
        try {
            // 使用资源管理器枚举实际的皮肤文件
            // 正确的资源路径应该是 "textures/entity/skins/"
            resourceManager.listResources("textures/entity/skins", path -> {
                String pathStr = path.getPath();
                return pathStr.endsWith(".png") || pathStr.endsWith(".svg");
            }).forEach((location, resource) -> {
                availableSkins.add(location);
                LOGGER.debug("发现皮肤文件: {}", location);
            });

            // 如果没有找到皮肤文件，添加默认皮肤
            if (availableSkins.isEmpty()) {
                LOGGER.info("未找到皮肤文件，添加默认皮肤");
                availableSkins.add(new ResourceLocation(MOD_ID, "textures/entity/skins/default_skin.png"));

                // 也添加一些内置的示例皮肤
                availableSkins.add(new ResourceLocation(MOD_ID, "textures/entity/skins/skin1.png"));
                availableSkins.add(new ResourceLocation(MOD_ID, "textures/entity/skins/skin2.png"));
                availableSkins.add(new ResourceLocation(MOD_ID, "textures/entity/skins/skin3.png"));
            }

            LOGGER.info("加载了 {} 个皮肤文件", availableSkins.size());
            isInitialized = true;
        } catch (Exception e) {
            LOGGER.error("加载皮肤文件时出错: {}", e.getMessage(), e);
            // 添加默认皮肤作为后备
            availableSkins.add(new ResourceLocation(MOD_ID, "textures/entity/skins/default_skin.png"));
            isInitialized = true;
        }
    }

    /**
     * 根据名字获取皮肤资源位置
     * @param name 皮肤名字（不包含路径和扩展名）
     * @return 对应皮肤的ResourceLocation，如果找不到则返回随机皮肤
     */
    public ResourceLocation getSkinByName(String name) {
        if (!isInitialized) {
            reloadSkins(net.minecraft.client.Minecraft.getInstance().getResourceManager());
        }

        // 构建预期的资源路径
        String expectedPath = "textures/entity/skins/" + name + ".png";
        LOGGER.debug("查找皮肤: {}", expectedPath);

        // 遍历所有可用皮肤，比较路径
        for (ResourceLocation availableSkin : availableSkins) {
            if (availableSkin.getPath().equals(expectedPath)) {
                LOGGER.debug("找到匹配的皮肤: {}", availableSkin);
                return availableSkin;
            }
        }

        LOGGER.warn("未找到皮肤: {}, 使用随机皮肤", name);
        return getRandomSkin();
    }


    /**
     * 随机获取一个皮肤资源位置
     * @return 随机皮肤的ResourceLocation，如果没有可用皮肤则返回null
     */
    public ResourceLocation getRandomSkin() {
        if (!isInitialized) {
            LOGGER.warn("皮肤加载器尚未初始化");
            // 尝试立即初始化
            reloadSkins(net.minecraft.client.Minecraft.getInstance().getResourceManager());
        }

        if (availableSkins.isEmpty()) {
            LOGGER.warn("没有可用的皮肤文件");
            return new ResourceLocation("textures/entity/steve.png");
        }

        int index = RANDOM.nextInt(availableSkins.size());
        return availableSkins.get(index);
    }


    /**
     * 获取所有可用皮肤
     */
    public List<ResourceLocation> getAllSkins() {
        return Collections.unmodifiableList(availableSkins);
    }

    /**
     * 获取可用皮肤数量
     */
    public int getAvailableSkinCount() {
        return availableSkins.size();
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}