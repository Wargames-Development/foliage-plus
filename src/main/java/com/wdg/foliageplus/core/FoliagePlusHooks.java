package com.wdg.foliageplus.core;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public final class FoliagePlusHooks {

    private FoliagePlusHooks() {}

    // Snapshot values used by the coremod hook (safe defaults)
    private static volatile boolean enabled = true;
    private static volatile boolean allowFullCover = true;
    private static volatile boolean affectAllEntities = false;

    public static void applyConfigSnapshot() {
        enabled = FoliagePlusConfig.enabled;
        allowFullCover = FoliagePlusConfig.allowFullCover;
        affectAllEntities = FoliagePlusConfig.affectAllEntities;
    }

    /**
     * Called from ASM-injected code in Block#addCollisionBoxesToList (obf/deobf).
     * Object signature avoids descriptor remap issues across coremod phases.
     */
    public static boolean shouldSkipCollision(Object blockObj, Object worldObj, int x, int y, int z, Object entityObj) {
        if (!enabled) {
            return false;
        }
        if (!(blockObj instanceof Block) || !(worldObj instanceof World) || !(entityObj instanceof Entity)) {
            return false;
        }

        Block block = (Block) blockObj;
        World world = (World) worldObj;
        Entity entity = (Entity) entityObj;

        if (!block.isLeaves(world, x, y, z)) {
            return false;
        }

        if (!affectAllEntities && !(entity instanceof EntityPlayer)) {
            return false;
        }

        if (!allowFullCover && (entity instanceof EntityPlayer)) {
            // If the player's feet and head are both within leaf blocks, do NOT allow them to be fully covered.
            if (isFullyCoveredByLeaves(entity, world)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isEntityInLeaves(Entity entity, World world) {
        if (entity == null || world == null) {
            return false;
        }

        int x = MathHelper.floor_double(entity.posX);
        int z = MathHelper.floor_double(entity.posZ);

        int yFeet = MathHelper.floor_double(entity.boundingBox.minY + 0.01D);
        int yHead = yFeet + 1;

        Block feet = world.getBlock(x, yFeet, z);
        Block head = world.getBlock(x, yHead, z);

        boolean inFeet = (feet != null) && feet.isLeaves(world, x, yFeet, z);
        boolean inHead = (head != null) && head.isLeaves(world, x, yHead, z);

        if (!allowFullCover && (inFeet && inHead) && (entity instanceof EntityPlayer)) {
            // When full-cover is disallowed, treat this as not-in-leaves for slowdown/sound purposes.
            return false;
        }

        return inFeet || inHead;
    }

    private static boolean isFullyCoveredByLeaves(Entity entity, World world) {
        int x = MathHelper.floor_double(entity.posX);
        int z = MathHelper.floor_double(entity.posZ);

        int yFeet = MathHelper.floor_double(entity.boundingBox.minY + 0.01D);
        int yHead = yFeet + 1;

        Block feet = world.getBlock(x, yFeet, z);
        Block head = world.getBlock(x, yHead, z);

        boolean inFeet = (feet != null) && feet.isLeaves(world, x, yFeet, z);
        boolean inHead = (head != null) && head.isLeaves(world, x, yHead, z);

        return inFeet && inHead;
    }
}
