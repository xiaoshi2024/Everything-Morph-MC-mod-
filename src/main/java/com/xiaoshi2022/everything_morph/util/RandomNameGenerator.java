package com.xiaoshi2022.everything_morph.util;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * 随机名字生成器 - 生成与皮肤文件匹配的随机名字
 * 支持万用皮肤补丁模组的命名约定
 */
public class RandomNameGenerator {
    private static final Random RANDOM = new Random();

    // 皮肤文件名列表
    private static final String[] SKIN_NAMES = {"skin1", "skin2", "skin3", "skin4", "skin5", "ufo", "morph", "phantom", "dragon", "cyber"};

    // 显示名称部件
    private static final String[] FIRST_PARTS = {"Shadow", "Light", "Dark", "Fire", "Ice", "Storm", "Iron", "Gold", "Cyber", "Phantom"};
    private static final String[] SECOND_PARTS = {"Warrior", "Mage", "Archer", "Knight", "Rogue", "Hunter", "Wizard", "Slayer", "Guardian", "Ninja"};

    // 玩家名核心部分
    private static final String[] CORE_NAMES = {
            "Steve", "Alex", "Nova", "Kai", "Rin", "Luna", "Jade", "Neo", "Zeno", "Mira",
            "UFO", "Echo", "Zero", "Void", "Orbit", "Nova", "Cosmo", "Astro", "Tech", "Byte"
    };

    // 特殊后缀和连接词
    private static final String[] SPECIAL_SUFFIXES = {"by", "from", "with", "the", "of", "and"};
    private static final String[] CREATOR_NAMES = {"shi", "nova", "echo", "zen", "kai", "luna", "max", "rex", "lex", "tex"};

    private static final RandomNameGenerator INSTANCE = new RandomNameGenerator();

    private RandomNameGenerator() {
        // 私有构造函数
    }

    public static RandomNameGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * 生成像真实玩家的 ID（支持特殊格式如 UFO_by_shi）
     */
    public String generateRandomPlayerName() {
        // 30% 概率生成特殊格式（如 UFO_by_shi）
        if (RANDOM.nextInt(100) < 30) {
            return generateSpecialFormatName();
        }

        // 70% 概率生成普通格式
        return generateNormalPlayerName();
    }

    /**
     * 生成特殊格式的名称（如 UFO_by_shi）
     */
    private String generateSpecialFormatName() {
        String core = CORE_NAMES[RANDOM.nextInt(CORE_NAMES.length)];
        String connector = SPECIAL_SUFFIXES[RANDOM.nextInt(SPECIAL_SUFFIXES.length)];
        String creator = CREATOR_NAMES[RANDOM.nextInt(CREATOR_NAMES.length)];

        // 随机选择格式
        int format = RANDOM.nextInt(4);
        switch (format) {
            case 0: return core + "_" + connector + "_" + creator;  // UFO_by_shi
            case 1: return core + connector + creator;              // UFObyshi
            case 2: return core + "_" + creator;                    // UFO_shi
            case 3: return creator + "_" + core;                    // shi_UFO
            default: return core + "_" + connector + "_" + creator;
        }
    }

    /**
     * 生成普通玩家名称
     */
    private String generateNormalPlayerName() {
        String[] tails = {"", "123", "Xx", "_", "02", "77", "HD", "LP", "VR", "xd", "Pro", "GG", "TTV"};

        String name;
        int attempts = 0;
        do {
            String core = CORE_NAMES[RANDOM.nextInt(CORE_NAMES.length)];
            String tail = tails[RANDOM.nextInt(tails.length)];

            // 随机决定是否添加数字
            if (RANDOM.nextBoolean() && core.length() < 10) {
                int numbers = RANDOM.nextInt(1000);
                name = core + numbers;
            } else {
                name = core + tail;
            }

            attempts++;
            // 保底机制：如果尝试太多次，使用默认名称
            if (attempts > 10) {
                name = "UFO_by_shi"; // 你的保底ID
                break;
            }

        } while (name.length() > 16 || name.length() < 3);

        return name;
    }

    // 修改 generateRandomSkinName 方法，确保生成的名称在资源包中存在
    public String generateRandomSkinName() {
        // 直接返回预设的皮肤文件名，确保100%匹配
        return SKIN_NAMES[RANDOM.nextInt(SKIN_NAMES.length)];
    }

    // 或者添加一个新方法专门用于实体皮肤加载
    public String generateSkinNameForEntity() {
        // 只返回资源包中确实存在的皮肤文件名
        String[] availableSkins = {"skin1", "skin2", "skin3", "skin4", "skin5", "default_skin"};
        return availableSkins[RANDOM.nextInt(availableSkins.length)];
    }

    /**
     * 生成万用皮肤补丁模组兼容的UUID格式名称
     */
    public String generateUniversalSkinName(String playerName) {
        String skinName = generateRandomSkinName();
        return playerName + "_" + skinName;
    }

    /**
     * 生成随机显示名字
     */
    public String generateRandomDisplayName() {
        // 20% 概率使用特殊格式
        if (RANDOM.nextInt(100) < 20) {
            return generateSpecialFormatName();
        }

        String first = FIRST_PARTS[RANDOM.nextInt(FIRST_PARTS.length)];
        String second = SECOND_PARTS[RANDOM.nextInt(SECOND_PARTS.length)];
        return first + " " + second;
    }

    /**
     * 获取所有可用的皮肤名字
     */
    public String[] getAvailableSkinNames() {
        return SKIN_NAMES.clone();
    }

    /**
     * 检查名称是否符合命名约定
     */
    public static boolean isValidUniversalSkinName(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.contains("_") && name.length() >= 3 && name.length() <= 32;
    }

    /**
     * 从完整名称中提取皮肤名称部分
     */
    public static String extractSkinNameFromUniversal(String universalName) {
        if (!isValidUniversalSkinName(universalName)) {
            return universalName;
        }

        int lastUnderscore = universalName.lastIndexOf('_');
        return universalName.substring(lastUnderscore + 1);
    }

    /**
     * 生成指定数量的随机玩家名（用于测试）
     */
    public List<String> generateMultiplePlayerNames(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(generateRandomPlayerName());
        }
        return names;
    }
}