package com.wdg.foliageplus.core;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class FoliagePlusConfig {

    private FoliagePlusConfig() {}

    public static boolean enabled = true;

    /**
     * 0.01 - 1.00, where 1.00 is normal speed.
     */
    public static double speedMultiplier = 0.50D;

    /**
     * If false: players cannot be fully covered by leaves (feet + head inside leaves).
     */
    public static boolean allowFullCover = true;

    /**
     * If true: all entities can pass through leaves; if false: only players.
     */
    public static boolean affectAllEntities = false;

    private static Configuration config;

    public static void load(File minecraftConfigDir) {
        File dir = new File(minecraftConfigDir, "foliage-plus");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        File cfgFile = new File(dir, "foliage-plus.cfg");
        config = new Configuration(cfgFile);

        sync();
    }

    public static void sync() {
        if (config == null) {
            return;
        }

        String cat = "general";
        config.setCategoryComment(cat,
                "Foliage+ configuration\n" +
                "Config path: /config/foliage-plus/foliage-plus.cfg\n" +
                "Values persist between restarts."
        );

        enabled = config.getBoolean(
                "enabled",
                cat,
                enabled,
                "Master toggle for Foliage+. If false, leaves behave normally."
        );

        speedMultiplier = config.getFloat(
                "speedMultiplier",
                cat,
                (float) speedMultiplier,
                0.01F,
                1.00F,
                "Movement multiplier when inside leaves. 1.00 = normal speed."
        );

        allowFullCover = config.getBoolean(
                "allowFullCover",
                cat,
                allowFullCover,
                "If false, players cannot be fully covered by leaves (both feet and head blocks are leaves)."
        );

        affectAllEntities = config.getBoolean(
                "affectAllEntities",
                cat,
                affectAllEntities,
                "If true, all entities can pass through leaves. If false, only players can."
        );

        if (config.hasChanged()) {
            config.save();
        }
    }
}
