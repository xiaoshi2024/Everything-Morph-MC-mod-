package com.xiaoshi2022.everything_morph.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin;

/**
 * 资源包皮肤加载器 - 从资源包中随机选择皮肤文件
 * 支持万用皮肤补丁模组的命名约定
 */
@OnlyIn(Dist.CLIENT)
public class ResourcePackSkinLoader implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourcePackSkinLoader INSTANCE = new ResourcePackSkinLoader();
    private static final Random RANDOM = new Random();
    private static final String MOD_ID = "everything_morph";
    private static final String SKIN_FOLDER = "textures/entity/skins/";

    // 支持的文件扩展名
    private static final Pattern SKIN_FILE_PATTERN = Pattern.compile(".*\\.(png|svg)$", Pattern.CASE_INSENSITIVE);

    // 万用皮肤补丁模组支持的皮肤前缀模式
    private static final Pattern UNIVERSAL_SKIN_PATTERN = Pattern.compile(
            "^(player|skin|character|avatar|mob|npc|hero|villager|warrior|mage)_[a-zA-Z0-9_]+$",
            Pattern.CASE_INSENSITIVE
    );

    private final List<ResourceLocation> availableSkins = new CopyOnWriteArrayList<>();
    private final Map<String, ResourceLocation> skinNameMap = new HashMap<>();
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
        skinNameMap.clear();

        // 查找所有皮肤文件
        try {
            // 使用资源管理器枚举实际的皮肤文件
            resourceManager.listResources("textures/entity/skins", path -> {
                String pathStr = path.getPath();
                return SKIN_FILE_PATTERN.matcher(pathStr).matches();
            }).forEach((location, resource) -> {
                availableSkins.add(location);

                // 提取皮肤名称（不带路径和扩展名）
                String skinName = extractSkinNameFromPath(location.getPath());
                skinNameMap.put(skinName, location);

                LOGGER.debug("发现皮肤文件: {} -> {}", skinName, location);
            });

            // 如果没有找到皮肤文件，添加默认皮肤
            if (availableSkins.isEmpty()) {
                LOGGER.info("未找到皮肤文件，添加默认皮肤");
                addDefaultSkins();
            }

            LOGGER.info("加载了 {} 个皮肤文件，映射了 {} 个皮肤名称",
                    availableSkins.size(), skinNameMap.size());
            isInitialized = true;
        } catch (Exception e) {
            LOGGER.error("加载皮肤文件时出错: {}", e.getMessage(), e);
            // 添加默认皮肤作为后备
            addDefaultSkins();
            isInitialized = true;
        }
    }

    private void addDefaultSkins() {
        // 添加默认皮肤
        ResourceLocation defaultSkin = new ResourceLocation(MOD_ID, "textures/entity/skins/default_skin.png");
        availableSkins.add(defaultSkin);
        skinNameMap.put("default_skin", defaultSkin);

        // 添加一些内置的示例皮肤
        String[] defaultSkins = {"skin1", "skin2", "skin3", "skin4", "skin5"};
        for (String skin : defaultSkins) {
            ResourceLocation location = new ResourceLocation(MOD_ID, "textures/entity/skins/" + skin + ".png");
            availableSkins.add(location);
            skinNameMap.put(skin, location);
        }
    }

    /**
     * 从文件路径中提取皮肤名称
     */
    private String extractSkinNameFromPath(String path) {
        // 移除路径前缀和文件扩展名
        String name = path.replace("textures/entity/skins/", "");
        name = name.replace(".png", "").replace(".svg", "");
        return name;
    }

    /**
     * 根据名字获取皮肤资源位置
     * 支持万用皮肤补丁模组的命名约定
     */
    // 修改 getSkinByName 方法，添加更好的回退机制
    public ResourceLocation getSkinByName(String name) {
        if (!isInitialized) {
            reloadSkins(net.minecraft.client.Minecraft.getInstance().getResourceManager());
        }

        if (name == null || name.isEmpty()) {
            LOGGER.warn("皮肤名称为空，使用默认皮肤");
            return getDefaultSkin();
        }

        // 1.20.1+ 检查是否是默认皮肤请求
        if (name.equals("default") || name.contains("steve")) {
            return getDefaultSkin();
        }

        if (name == null || name.isEmpty()) {
            LOGGER.warn("皮肤名称为空，使用随机皮肤");
            return getRandomSkin();
        }

        LOGGER.debug("查找皮肤: {}", name);

        // 1. 首先尝试精确匹配
        ResourceLocation exactMatch = skinNameMap.get(name);
        if (exactMatch != null) {
            LOGGER.debug("找到精确匹配的皮肤: {}", exactMatch);
            return exactMatch;
        }

        // 2. 尝试小写匹配（很多资源包使用小写文件名）
        ResourceLocation lowerCaseMatch = skinNameMap.get(name.toLowerCase());
        if (lowerCaseMatch != null) {
            LOGGER.debug("找到小写匹配的皮肤: {} -> {}", name, lowerCaseMatch);
            return lowerCaseMatch;
        }

        // 3. 如果是万用皮肤格式，尝试提取基本名称
        if (isUniversalSkinName(name)) {
            String baseName = extractBaseSkinName(name);
            ResourceLocation baseMatch = skinNameMap.get(baseName);
            if (baseMatch != null) {
                LOGGER.debug("找到基础皮肤匹配: {} -> {}", name, baseMatch);
                return baseMatch;
            }
        }

        // 4. 尝试前缀匹配
        for (Map.Entry<String, ResourceLocation> entry : skinNameMap.entrySet()) {
            if (name.startsWith(entry.getKey()) || entry.getKey().startsWith(name)) {
                LOGGER.debug("找到前缀匹配的皮肤: {} -> {}", name, entry.getValue());
                return entry.getValue();
            }
        }

        LOGGER.warn("未找到皮肤: {}, 使用随机皮肤", name);
        return getRandomSkin();
    }

    /**
     * 检查是否为万用皮肤补丁模组格式的名称
     */
    private boolean isUniversalSkinName(String name) {
        return UNIVERSAL_SKIN_PATTERN.matcher(name).matches();
    }

    /**
     * 从万用皮肤名称中提取基础皮肤名称
     */
    private String extractBaseSkinName(String universalName) {
        // 移除前缀部分，只保留基础名称
        String[] parts = universalName.split("_", 2);
        if (parts.length > 1) {
            return parts[1];
        }
        return universalName;
    }

    /**
     * 随机获取一个皮肤资源位置
     */
    public ResourceLocation getRandomSkin() {
        if (!isInitialized) {
            LOGGER.warn("皮肤加载器尚未初始化");
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
     * 获取所有可用的皮肤名称（不包含路径和扩展名）
     */
    public Set<String> getAllSkinNames() {
        return Collections.unmodifiableSet(skinNameMap.keySet());
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

    /**
     * 检查皮肤是否存在
     */
    public boolean hasSkin(String name) {
        return skinNameMap.containsKey(name);
    }
}