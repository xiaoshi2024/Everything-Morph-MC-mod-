package com.xiaoshi2022.everything_morph.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private Path externalSkinDir;

    private ResourcePackSkinLoader() {
        // 私有构造函数，使用单例模式
        // 使用Minecraft游戏目录来构建外部皮肤目录的绝对路径
        Path minecraftDir = null;
        try {
            // 在Forge中，可以通过Minecraft.getInstance().gameDirectory获取游戏目录
            minecraftDir = Paths.get(net.minecraft.client.Minecraft.getInstance().gameDirectory.getAbsolutePath());
        } catch (Exception e) {
            // 如果无法获取游戏目录，则使用当前工作目录
            minecraftDir = Paths.get(System.getProperty("user.dir"));
            LOGGER.warn("无法获取Minecraft游戏目录，使用当前工作目录: {}", minecraftDir);
        }
        this.externalSkinDir = minecraftDir.resolve("config/everything_morph/skins");
        LOGGER.info("外部皮肤目录: {}", this.externalSkinDir.toAbsolutePath());
    }

    public static ResourcePackSkinLoader getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化皮肤加载器
     */
    public void initialize() {
        // 确保外部皮肤目录存在
        try {
            if (!Files.exists(externalSkinDir)) {
                Files.createDirectories(externalSkinDir);
                LOGGER.info("创建外部皮肤目录: {}", externalSkinDir.toAbsolutePath());
            }

            // 初始加载皮肤
            reloadSkins(net.minecraft.client.Minecraft.getInstance().getResourceManager());
            isInitialized = true;
        } catch (IOException e) {
            LOGGER.error("无法创建外部皮肤目录", e);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // 在资源管理器重载时重新加载皮肤列表
        reloadSkins(resourceManager);
    }

    private void reloadSkins(ResourceManager resourceManager) {
        availableSkins.clear();
        skinNameMap.clear();

        // 加载内置资源包皮肤
        loadBuiltInSkins(resourceManager);

        // 加载外部文件夹皮肤
        loadExternalSkins();

        // 如果没有找到皮肤文件，添加默认皮肤
        if (availableSkins.isEmpty()) {
            LOGGER.info("未找到皮肤文件，添加默认皮肤");
            addDefaultSkins();
        }

        LOGGER.info("加载了 {} 个皮肤文件，映射了 {} 个皮肤名称",
                availableSkins.size(), skinNameMap.size());
        isInitialized = true;
    }

    /**
     * 加载内置资源包中的皮肤
     */
    private void loadBuiltInSkins(ResourceManager resourceManager) {
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

                LOGGER.debug("发现内置皮肤文件: {} -> {}", skinName, location);
            });
        } catch (Exception e) {
            LOGGER.error("加载内置皮肤文件时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 扫描外部皮肤文件夹：config/everything_morph/skins/
     * 使用正确的资源位置映射
     */
    // 1. 整个方法留空或删除
    private void loadExternalSkins() {
        LOGGER.info("扫描外部皮肤文件夹: {}", externalSkinDir.toAbsolutePath());

        if (!Files.exists(externalSkinDir) || !Files.isDirectory(externalSkinDir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(externalSkinDir, "*.{png,svg}")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;

                String fileName = file.getFileName().toString();
                String skinName = fileName.replaceAll("(?i)\\.(png|svg)$", "");

                // ✅ 注册成合规资源位置
                ResourceLocation location = new ResourceLocation("everything_morph_external",
                        "textures/entity/skins/" + skinName + ".png");

                availableSkins.add(location);
                skinNameMap.put(skinName, location);
                skinNameMap.put("external_" + skinName, location);

                LOGGER.info("✅ 注册外部皮肤: {} -> {}", skinName, location);
            }
        } catch (IOException e) {
            LOGGER.error("扫描外部皮肤文件夹失败", e);
        }
    }


    private void addDefaultSkins() {
        // 添加默认皮肤
        ResourceLocation defaultSkin = new ResourceLocation("textures/entity/steve.png");
        availableSkins.add(defaultSkin);
        skinNameMap.put("default", defaultSkin);

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

    public ResourceLocation getSkinByName(String name) {
        LOGGER.info("🔍 深度查找皮肤: {}", name);

        if (!isInitialized) {
            initialize();
        }

        // 1. 首先尝试精确匹配
        ResourceLocation exactMatch = skinNameMap.get(name);
        if (exactMatch != null) {
            LOGGER.info("✅ 精确匹配找到皮肤: {} -> {}", name, exactMatch);
            return exactMatch;
        }

        // 2. 检查外部皮肤目录中的实际文件
        Path skinFile = externalSkinDir.resolve(name + ".png");
        if (Files.exists(skinFile)) {
            LOGGER.info("✅ 找到外部皮肤文件: {}", skinFile);
            // 返回已经在 loadExternalSkins() 中注册的资源位置
            // 确保这个资源位置已经在映射表中
            return skinNameMap.get(name);
        }

        // 3. 尝试小写匹配
        String lowerName = name.toLowerCase();
        ResourceLocation lowerCaseMatch = skinNameMap.get(lowerName);
        if (lowerCaseMatch != null) {
            LOGGER.info("✅ 小写匹配找到皮肤: {} -> {}", lowerName, lowerCaseMatch);
            return lowerCaseMatch;
        }

        LOGGER.warn("❌ 未找到皮肤: {}, 使用默认皮肤", name);
        return getDefaultSkin();
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
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        // 检查直接匹配
        if (skinNameMap.containsKey(name)) {
            return true;
        }
        
        // 检查小写匹配
        if (skinNameMap.containsKey(name.toLowerCase())) {
            return true;
        }
        
        // 检查外部皮肤特殊前缀
        String externalKey = "external_" + name;
        if (skinNameMap.containsKey(externalKey) || skinNameMap.containsKey(externalKey.toLowerCase())) {
            return true;
        }
        
        // 检查文件是否实际存在于外部皮肤目录
        try {
            Path skinFile = externalSkinDir.resolve(name + ".png");
            if (Files.exists(skinFile) && Files.isRegularFile(skinFile)) {
                return true;
            }
            // 尝试不带扩展名的文件名
            skinFile = externalSkinDir.resolve(name);
            return Files.exists(skinFile) && Files.isRegularFile(skinFile);
        } catch (Exception e) {
            LOGGER.error("检查皮肤文件存在性时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 重新加载外部皮肤（用于指令调用）
     */
    public void reloadExternalSkins() {
        LOGGER.info("重新加载外部皮肤...");

        // 清除当前外部皮肤（使用正确的路径 skins/）
        availableSkins.removeIf(loc -> loc.getPath().startsWith("skins/"));
        skinNameMap.entrySet().removeIf(entry ->
                entry.getValue().getPath().startsWith("skins/") ||
                        entry.getKey().startsWith("external_")
        );

        // 重新加载外部皮肤
        loadExternalSkins();

        LOGGER.info("重新加载完成，现有 {} 个皮肤", availableSkins.size());
    }
}