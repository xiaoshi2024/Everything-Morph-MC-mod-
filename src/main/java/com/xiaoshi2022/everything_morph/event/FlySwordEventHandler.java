package com.xiaoshi2022.everything_morph.event;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class FlySwordEventHandler {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
}
