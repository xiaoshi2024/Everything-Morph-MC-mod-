package com.xiaoshi2022.everything_morph.util;

import java.util.Random;

/**
 * 随机名字生成器 - 生成与皮肤文件匹配的随机名字
 */
public class RandomNameGenerator {
    private static final Random RANDOM = new Random();

    // 皮肤文件名列表 - 必须与resources中的皮肤文件一致
    private static final String[] SKIN_NAMES = {"skin1", "skin2", "skin3", "skin4", "skin5"};

    // 显示名称部件（用于实体显示名称）
    private static final String[] FIRST_PARTS = {"Shadow", "Light", "Dark", "Fire", "Ice", "Storm", "Iron", "Gold"};
    private static final String[] SECOND_PARTS = {"Warrior", "Mage", "Archer", "Knight", "Rogue", "Hunter", "Wizard"};

    private static final RandomNameGenerator INSTANCE = new RandomNameGenerator();

    private RandomNameGenerator() {
        // 私有构造函数，使用单例模式
    }

    public static RandomNameGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * 生成随机皮肤文件名（用于加载皮肤）
     */
    public String generateRandomSkinName() {
        return SKIN_NAMES[RANDOM.nextInt(SKIN_NAMES.length)];
    }

    /**
     * 生成随机显示名字（用于实体名称显示）
     */
    public String generateRandomDisplayName() {
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
}