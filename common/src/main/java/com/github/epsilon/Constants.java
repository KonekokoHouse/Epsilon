package com.github.epsilon;

import com.github.epsilon.interfaces.MinecraftTimerAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Constants {

    public static final Minecraft mc = Minecraft.getInstance();

    public static final String NAME = "Epsilon";

    public static final String MOD_ID = BuildConfig.MOD_ID;

    public static final String VERSION = BuildConfig.VERSION;

    public static final Logger LOGGER = LogManager.getLogger(Constants.NAME);

    public static DeltaTracker getDeltaTracker() {
        return ((MinecraftTimerAccessor) mc).epsilon$getTimer();
    }

}
