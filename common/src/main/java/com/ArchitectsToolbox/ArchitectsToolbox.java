package com.ArchitectsToolbox;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** The mod's id and log, shared by both loaders' entry points. */
public final class ArchitectsToolbox {
    public static final String MOD_ID = "architects_toolbox";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ArchitectsToolbox() {
    }
}
