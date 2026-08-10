package com.soulsouls;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoulSouls implements ModInitializer {
    public static final String MOD_ID = "soulsouls";
    public static final Logger LOGGER = LoggerFactory.getLogger("Soul Souls");

    @Override
    public void onInitialize() {
        LOGGER.info("[Soul Souls] bootstrapping");
    }
}
