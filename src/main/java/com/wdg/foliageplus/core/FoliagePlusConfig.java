package com.wdg.foliageplus.core;

import net.minecraftforge.common.config.Configuration;
import net.minecraft.world.World;

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

    /**
     * Logs one detailed line when an entity transitions into foliage contact.
     * Intended only for reproducing contact bugs; disabled by default.
     */
    public static boolean debugContactTransitions = false;

    private static Configuration config;
    private static volatile Settings localSettings = new Settings(true, true, false, 0.5D);
    private static volatile Settings serverSettings;

    /** Immutable publication keeps the integrated server and client settings separate. */
    public static final class Settings {
        public final boolean enabled;
        public final boolean allowFullCover;
        public final boolean affectAllEntities;
        public final double speedMultiplier;

        public Settings(boolean enabled, boolean allowFullCover, boolean affectAllEntities, double speedMultiplier) {
            this.enabled = enabled;
            this.allowFullCover = allowFullCover;
            this.affectAllEntities = affectAllEntities;
            this.speedMultiplier = Double.isNaN(speedMultiplier) || Double.isInfinite(speedMultiplier)
                    ? 1.0D : Math.max(0.01D, Math.min(1.0D, speedMultiplier));
        }
    }

    public static Settings getSettings(World world) {
        Settings remote = serverSettings;
        return world != null && world.isRemote && remote != null ? remote : localSettings;
    }

    public static void applyLocalSnapshot() {
        localSettings = new Settings(enabled, allowFullCover, affectAllEntities, speedMultiplier);
    }

    public static void setServerSettings(Settings settings) {
        serverSettings = settings;
    }

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

        debugContactTransitions = config.getBoolean(
                "debugContactTransitions",
                cat,
                debugContactTransitions,
                "If true, log one detailed diagnostic line when an entity enters foliage contact. Disabled by default."
        );

        if (config.hasChanged()) {
            config.save();
        }
        applyLocalSnapshot();
    }
}
