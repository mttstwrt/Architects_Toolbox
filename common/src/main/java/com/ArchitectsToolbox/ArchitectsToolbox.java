package com.ArchitectsToolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchitectsToolbox {
    public static final String MOD_ID = "architects_toolbox";
    public static final String MOD_NAME = "Architect's Toolbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Hello from Architect's Toolbox!");
    }
}
