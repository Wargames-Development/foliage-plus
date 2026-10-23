package com.wdg.foliageplus.core;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FoliagePlusHooks {

    private FoliagePlusHooks() {}

    /**
     * A normal player is about 0.6 blocks wide. Requiring more than 0.01 blocks
     * of overlap on every axis rejects face/edge contact and movement rounding,
     * while still activating after only a very small (about 1.7% of player width)
     * entry into a leaf cell.
     */
    private static final double CONTACT_EPSILON = 0.01D;
    private static final double LEGACY_CONTRACTION = 0.001D;
    private static final double WORLD_LIMIT = 30000000.0D;
    private static final long MAX_CONTACT_CELLS = 4096L;

    private static final LeafContact MATCH_ONLY = new LeafContact();
    private static final Map<Entity, DebugContactState> DEBUG_CONTACT_STATES =
            new WeakHashMap<Entity, DebugContactState>();

    public static void applyConfigSnapshot() {
        FoliagePlusConfig.applyLocalSnapshot();
    }

    /**
     * Called from ASM-injected code in Block#addCollisionBoxesToList (obf/deobf).
     * Object signature avoids descriptor remap issues across coremod phases.
     */
    public static boolean shouldSkipCollision(Object blockObj, Object worldObj, int x, int y, int z, Object entityObj) {
        if (!(blockObj instanceof Block) || !(worldObj instanceof World) || !(entityObj instanceof Entity)) {
            return false;
        }

        Block block = (Block) blockObj;
        World world = (World) worldObj;
        Entity entity = (Entity) entityObj;
        FoliagePlusConfig.Settings settings = FoliagePlusConfig.getSettings(world);

        if (!settings.enabled || entity.worldObj != world || !isFoliage(block, world, x, y, z)) {
            return false;
        }

        if (!settings.affectAllEntities && !(entity instanceof EntityPlayer)) {
            return false;
        }

        // allowFullCover=false behavior:
        // If a leaf block has another leaf block directly above it, the LOWER one becomes solid again.
        // This prevents being "fully inside" a 2-high bush while still allowing walking through top leaves.
        if (!settings.allowFullCover && (entity instanceof EntityPlayer) && y < 255
                && world.blockExists(x, y + 1, z)) {
            Block above = world.getBlock(x, y + 1, z);
            if (isFoliage(above, world, x, y + 1, z)) {
                return false; // bottom of a leaf column: keep collision
            }
        }

        return true; // leaf is pass-through
    }

    /**
     * isLeaves alone also includes NTM's invisible floodlight beams and EFR's
     * solid shroomlight/wart blocks. Require a real leaf type or leaf material.
     * This also supports EFR's generic, material-based mangrove/pale oak blocks.
     */
    private static boolean isFoliage(Block block, World world, int x, int y, int z) {
        return block != null && (block instanceof BlockLeaves || block.getMaterial() == Material.leaves)
                && !block.isAir(world, x, y, z) && block.isLeaves(world, x, y, z);
    }

    /** World call-site hook also covers leaf subclasses that never call super. */
    @SuppressWarnings("unchecked")
    public static void addCollisionBoxes(Object blockObj, Object worldObj, int x, int y, int z,
                                         Object maskObj, Object listObj, Object entityObj) {
        if (!shouldSkipCollision(blockObj, worldObj, x, y, z, entityObj)) {
            ((Block) blockObj).addCollisionBoxesToList((World) worldObj, x, y, z,
                    (AxisAlignedBB) maskObj, (List) listObj, (Entity) entityObj);
        }
    }

    /**
     * Canonical foliage-contact decision used by slowdown and rustling.
     *
     * Leaf blocks are tested against their stable one-block world cell instead
     * of mutable bounds stored on the singleton Block instance. This is also
     * consistent with Foliage+'s collision hook, which treats an eligible leaf
     * coordinate as the pass-through foliage cell. No block bounds are mutated.
     */
    public static boolean isEntityInLeaves(Entity entity, World world) {
        return findCanonicalLeafContact(entity, world, false) != null;
    }

    /**
     * Records only contact-entry transitions when the debug option is enabled.
     * While the canonical test is false it also evaluates the previous raw-block-
     * bounds algorithm, without applying its result, so a legacy false positive
     * can be tied to the exact block and stale bounds that would have matched.
     */
    public static void recordContactTransition(Entity entity, World world, boolean inLeaves,
                                               boolean moving, boolean slowdownApplied, boolean soundPlayed) {
        if (!FoliagePlusConfig.debugContactTransitions || entity == null || world == null) {
            return;
        }

        LeafContact canonicalContact = inLeaves ? findCanonicalLeafContact(entity, world, true) : null;
        LeafContact legacyContact = !inLeaves ? findLegacyLeafContact(entity, world) : null;
        boolean legacyMismatch = legacyContact != null;

        boolean canonicalEntering;
        boolean legacyMismatchEntering;
        synchronized (DEBUG_CONTACT_STATES) {
            DebugContactState state = DEBUG_CONTACT_STATES.get(entity);
            if (state == null) {
                state = new DebugContactState();
                DEBUG_CONTACT_STATES.put(entity, state);
            }

            canonicalEntering = inLeaves && !state.canonicalInLeaves;
            legacyMismatchEntering = legacyMismatch && !state.legacyMismatch;
            state.canonicalInLeaves = inLeaves;
            state.legacyMismatch = legacyMismatch;
        }

        if (canonicalEntering) {
            if (canonicalContact == null || canonicalContact == MATCH_ONLY) {
                logRecaptureFailure(entity, world, "CANONICAL");
            } else {
                logContact("leaf-contact-enter", "CANONICAL", entity, world, canonicalContact,
                        moving, slowdownApplied, soundPlayed);
            }
        }

        if (legacyMismatchEntering) {
            logContact("leaf-contact-legacy-mismatch", "LEGACY_REJECTED", entity, world, legacyContact,
                    moving, slowdownApplied, soundPlayed);
        }
    }

    public static void clearDebugState(Entity entity) {
        if (entity == null) {
            return;
        }
        synchronized (DEBUG_CONTACT_STATES) {
            DEBUG_CONTACT_STATES.remove(entity);
        }
    }

    public static void clearDebugState(World world) {
        if (world == null) {
            return;
        }
        synchronized (DEBUG_CONTACT_STATES) {
            Iterator<Entity> iterator = DEBUG_CONTACT_STATES.keySet().iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                if (entity == null || entity.worldObj == world) {
                    iterator.remove();
                }
            }
        }
    }

    private static LeafContact findCanonicalLeafContact(Entity entity, World world, boolean captureDetails) {
        if (entity == null || world == null || entity.worldObj != world || entity.boundingBox == null) {
            return null;
        }
        FoliagePlusConfig.Settings settings = FoliagePlusConfig.getSettings(world);
        if (!settings.enabled || (!settings.affectAllEntities && !(entity instanceof EntityPlayer))) {
            return null;
        }

        AxisAlignedBB box = entity.boundingBox;
        if (!isValidBox(box)) {
            return null;
        }

        // Contract logically, without allocating or modifying the entity AABB.
        double innerMinX = box.minX + CONTACT_EPSILON;
        double innerMinY = box.minY + CONTACT_EPSILON;
        double innerMinZ = box.minZ + CONTACT_EPSILON;
        double innerMaxX = box.maxX - CONTACT_EPSILON;
        double innerMaxY = box.maxY - CONTACT_EPSILON;
        double innerMaxZ = box.maxZ - CONTACT_EPSILON;

        if (innerMinX >= innerMaxX || innerMinY >= innerMaxY || innerMinZ >= innerMaxZ) {
            return null;
        }

        int minX = MathHelper.floor_double(innerMinX);
        int maxX = MathHelper.floor_double(innerMaxX);
        int minY = Math.max(0, MathHelper.floor_double(innerMinY));
        int maxY = Math.min(255, MathHelper.floor_double(innerMaxY));
        int minZ = MathHelper.floor_double(innerMinZ);
        int maxZ = MathHelper.floor_double(innerMaxZ);

        if (minY > maxY || (long) (maxX - minX + 1) * (maxY - minY + 1)
                * (maxZ - minZ + 1) > MAX_CONTACT_CELLS) {
            return null;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // blockExists is checked before getBlock so contact checks do not
                    // request or generate chunks outside the already loaded area.
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }

                    Block block = world.getBlock(x, y, z);
                    if (!shouldSkipCollision(block, world, x, y, z, entity)) {
                        continue;
                    }

                    double cellMinX = x;
                    double cellMinY = y;
                    double cellMinZ = z;
                    double cellMaxX = x + 1.0D;
                    double cellMaxY = y + 1.0D;
                    double cellMaxZ = z + 1.0D;
                    double overlapX = overlap(box.minX, box.maxX, cellMinX, cellMaxX);
                    double overlapY = overlap(box.minY, box.maxY, cellMinY, cellMaxY);
                    double overlapZ = overlap(box.minZ, box.maxZ, cellMinZ, cellMaxZ);

                    if (overlapX <= CONTACT_EPSILON || overlapY <= CONTACT_EPSILON || overlapZ <= CONTACT_EPSILON) {
                        continue;
                    }

                    if (!captureDetails) {
                        return MATCH_ONLY;
                    }

                    return new LeafContact(x, y, z, block, world.getBlockMetadata(x, y, z),
                            cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ,
                            overlapX, overlapY, overlapZ);
                }
            }
        }

        return null;
    }

    /**
     * Debug-only reproduction of the previous contact algorithm. Its result is
     * never used for gameplay. Reading raw bounds here is intentional so the
     * diagnostic can prove which singleton bounds the old code would accept.
     */
    private static LeafContact findLegacyLeafContact(Entity entity, World world) {
        if (entity == null || world == null || entity.boundingBox == null || !isValidBox(entity.boundingBox)) {
            return null;
        }

        AxisAlignedBB box = entity.boundingBox;
        double legacyMinX = box.minX + LEGACY_CONTRACTION;
        double legacyMinY = box.minY + LEGACY_CONTRACTION;
        double legacyMinZ = box.minZ + LEGACY_CONTRACTION;
        double legacyMaxX = box.maxX - LEGACY_CONTRACTION;
        double legacyMaxY = box.maxY - LEGACY_CONTRACTION;
        double legacyMaxZ = box.maxZ - LEGACY_CONTRACTION;

        if (legacyMinX >= legacyMaxX || legacyMinY >= legacyMaxY || legacyMinZ >= legacyMaxZ) {
            return null;
        }

        int minX = MathHelper.floor_double(legacyMinX);
        int maxX = MathHelper.floor_double(legacyMaxX);
        int minY = Math.max(0, MathHelper.floor_double(legacyMinY));
        int maxY = Math.min(255, MathHelper.floor_double(legacyMaxY));
        int minZ = MathHelper.floor_double(legacyMinZ);
        int maxZ = MathHelper.floor_double(legacyMaxZ);

        if (minY > maxY || (long) (maxX - minX + 1) * (maxY - minY + 1)
                * (maxZ - minZ + 1) > MAX_CONTACT_CELLS) {
            return null;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }

                    Block block = world.getBlock(x, y, z);
                    if (block == null || !block.isLeaves(world, x, y, z)) {
                        continue;
                    }

                    double contactMinX = x + block.getBlockBoundsMinX();
                    double contactMinY = y + block.getBlockBoundsMinY();
                    double contactMinZ = z + block.getBlockBoundsMinZ();
                    double contactMaxX = x + block.getBlockBoundsMaxX();
                    double contactMaxY = y + block.getBlockBoundsMaxY();
                    double contactMaxZ = z + block.getBlockBoundsMaxZ();

                    if (!isValidContactVolume(contactMinX, contactMinY, contactMinZ,
                            contactMaxX, contactMaxY, contactMaxZ)) {
                        continue;
                    }

                    double overlapX = overlap(legacyMinX, legacyMaxX, contactMinX, contactMaxX);
                    double overlapY = overlap(legacyMinY, legacyMaxY, contactMinY, contactMaxY);
                    double overlapZ = overlap(legacyMinZ, legacyMaxZ, contactMinZ, contactMaxZ);

                    // Match the original AABB intersection literally: it could
                    // accept NTM's zero-volume beam point inside an entity even
                    // though that block has no actual collision volume.
                    if (!(legacyMaxX > contactMinX && legacyMinX < contactMaxX
                            && legacyMaxY > contactMinY && legacyMinY < contactMaxY
                            && legacyMaxZ > contactMinZ && legacyMinZ < contactMaxZ)) {
                        continue;
                    }

                    return new LeafContact(x, y, z, block, world.getBlockMetadata(x, y, z),
                            contactMinX, contactMinY, contactMinZ,
                            contactMaxX, contactMaxY, contactMaxZ,
                            overlapX, overlapY, overlapZ);
                }
            }
        }

        return null;
    }

    private static void logContact(String eventName, String decision, Entity entity, World world,
                                   LeafContact contact, boolean moving,
                                   boolean slowdownApplied, boolean soundPlayed) {
        Object registryObject = Block.blockRegistry.getNameForObject(contact.block);
        String registryName = registryObject == null ? "<unregistered>" : String.valueOf(registryObject);
        AxisAlignedBB box = entity.boundingBox;

        FMLLog.info(
                "[FoliagePlus] %s decision=%s entity=%s uuid=%s class=%s side=%s dimension=%d " +
                "pos=(%.5f,%.5f,%.5f) entityAabb=(%.5f,%.5f,%.5f -> %.5f,%.5f,%.5f) " +
                "block=(%d,%d,%d) registry=%s blockClass=%s metadata=%d isLeaves=true " +
                "contactAabb=(%.5f,%.5f,%.5f -> %.5f,%.5f,%.5f) overlap=(%.5f,%.5f,%.5f) " +
                "moving=%s slowdown=%s sound=%s",
                eventName, decision,
                safeEntityName(entity), String.valueOf(entity.getUniqueID()), entity.getClass().getName(),
                world.isRemote ? "CLIENT" : "SERVER", getDimensionId(world),
                entity.posX, entity.posY, entity.posZ,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                contact.x, contact.y, contact.z, registryName, contact.block.getClass().getName(), contact.metadata,
                contact.minX, contact.minY, contact.minZ,
                contact.maxX, contact.maxY, contact.maxZ,
                contact.overlapX, contact.overlapY, contact.overlapZ,
                Boolean.valueOf(moving), Boolean.valueOf(slowdownApplied), Boolean.valueOf(soundPlayed));
    }

    private static void logRecaptureFailure(Entity entity, World world, String decision) {
        FMLLog.warning(
                "[FoliagePlus] leaf-contact-enter could not recapture matched block: decision=%s entity=%s uuid=%s class=%s side=%s dimension=%d",
                decision, safeEntityName(entity), String.valueOf(entity.getUniqueID()), entity.getClass().getName(),
                world.isRemote ? "CLIENT" : "SERVER", getDimensionId(world));
    }

    private static boolean isValidBox(AxisAlignedBB box) {
        return isFinite(box.minX) && isFinite(box.minY) && isFinite(box.minZ)
                && isFinite(box.maxX) && isFinite(box.maxY) && isFinite(box.maxZ)
                && box.minX < box.maxX && box.minY < box.maxY && box.minZ < box.maxZ
                && box.minX >= -WORLD_LIMIT && box.maxX <= WORLD_LIMIT
                && box.minZ >= -WORLD_LIMIT && box.maxZ <= WORLD_LIMIT;
    }

    private static boolean isValidContactVolume(double minX, double minY, double minZ,
                                                double maxX, double maxY, double maxZ) {
        return isFinite(minX) && isFinite(minY) && isFinite(minZ)
                && isFinite(maxX) && isFinite(maxY) && isFinite(maxZ)
                && minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int getDimensionId(World world) {
        return world.provider == null ? 0 : world.provider.dimensionId;
    }

    private static String safeEntityName(Entity entity) {
        String name = entity.getCommandSenderName();
        return name == null || name.length() == 0 ? "<unnamed>" : name;
    }

    private static final class DebugContactState {
        private boolean canonicalInLeaves;
        private boolean legacyMismatch;
    }

    private static final class LeafContact {
        private final int x;
        private final int y;
        private final int z;
        private final Block block;
        private final int metadata;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final double overlapX;
        private final double overlapY;
        private final double overlapZ;

        private LeafContact() {
            this(0, 0, 0, null, 0,
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D);
        }

        private LeafContact(int x, int y, int z, Block block, int metadata,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ,
                            double overlapX, double overlapY, double overlapZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.overlapX = overlapX;
            this.overlapY = overlapY;
            this.overlapZ = overlapZ;
        }
    }
}
