package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import com.wdg.foliageplus.core.FoliagePlusHooks;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class FoliagePlusTickHandler {

    private static final UUID SPEED_MOD_UUID = UUID.fromString("b6a1e51f-6b44-4b5f-b97f-0a6a56c63571");
    private static final String SPEED_MOD_NAME = "FoliagePlusSlow";

    private static final double SPEED_SMOOTH_STEP = 0.06D;
    private static final double SOUND_MOVEMENT_THRESHOLD_SQUARED = 0.0008D;

    /**
     * All maps are server-only and entity-identity keyed. Weak keys prevent
     * replaced/dead entities from being retained if an external removal path is
     * missed, and client/server player copies can never share one UUID entry.
     */
    private final Map<EntityPlayer, Integer> serverSoundCooldown = new WeakHashMap<EntityPlayer, Integer>();
    private final Map<EntityPlayer, PlayerPosition> serverPlayerPositions = new WeakHashMap<EntityPlayer, PlayerPosition>();
    private final Map<EntityLivingBase, Double> currentSpeedMultipliers = new WeakHashMap<EntityLivingBase, Double>();

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving == null) {
            return;
        }

        EntityLivingBase living = event.entityLiving;
        World world = living.worldObj;
        if (world == null || world.isRemote) {
            return;
        }

        // Players are evaluated after their movement at PlayerTick END. Running
        // both paths would smooth twice and diagnose a different contact sample.
        if (living instanceof EntityPlayer) {
            return;
        }

        FoliagePlusConfig.Settings settings = FoliagePlusConfig.getSettings(world);
        if (!settings.enabled) {
            clearSpeedState(living);
            FoliagePlusHooks.clearDebugState(living);
            return;
        }

        if (!settings.affectAllEntities) {
            clearSpeedState(living);
            FoliagePlusHooks.clearDebugState(living);
            return;
        }

        if (living.isDead || living.getHealth() <= 0.0F) {
            clearEntityState(living);
            return;
        }

        if (living.noClip || living.ridingEntity != null) {
            clearSpeedState(living);
            return;
        }

        boolean inLeaves = FoliagePlusHooks.isEntityInLeaves(living, world);
        double targetMultiplier = inLeaves ? settings.speedMultiplier : 1.0D;

        updateSpeedModifierSmooth(living, targetMultiplier);

        FoliagePlusHooks.recordContactTransition(
                    living,
                    world,
                    inLeaves,
                    isMovingHorizontally(living),
                    hasSpeedModifier(living),
                    false);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER || event.player == null) {
            return;
        }

        EntityPlayer player = event.player;
        World world = player.worldObj;
        if (world == null || world.isRemote) {
            return;
        }

        FoliagePlusConfig.Settings settings = FoliagePlusConfig.getSettings(world);
        if (!settings.enabled || player.isDead || player.getHealth() <= 0.0F) {
            clearEntityState(player);
            return;
        }

        boolean inLeaves = FoliagePlusHooks.isEntityInLeaves(player, world);
        if (player.capabilities.isFlying || player.noClip || player.ridingEntity != null) {
            clearSpeedState(player);
        } else {
            updateSpeedModifierSmooth(player, inLeaves ? settings.speedMultiplier : 1.0D);
        }
        boolean moving = updateAndCheckServerMovement(player);
        boolean soundPlayed = false;

        if (!inLeaves || !moving || player.noClip || player.ridingEntity != null) {
            // A new genuine movement/contact episode should be able to rustle
            // immediately rather than inheriting stale cooldown state.
            serverSoundCooldown.remove(player);
        } else {
            Integer cooldown = serverSoundCooldown.get(player);
            int remaining = cooldown == null ? 0 : cooldown.intValue();

            if (remaining > 0) {
                serverSoundCooldown.put(player, Integer.valueOf(remaining - 1));
            } else {
                float pitch = 0.9F + (world.rand.nextFloat() * 0.2F);
                double soundX = player.posX + (world.rand.nextDouble() - 0.5D) * 0.6D;
                double soundZ = player.posZ + (world.rand.nextDouble() - 0.5D) * 0.6D;

                // Server-side playSoundEffect broadcasts exactly one positioned
                // rustle to nearby clients, preserving remote-player audibility
                // without every client independently ticking remote players.
                world.playSoundEffect(soundX, player.posY + 1.0D, soundZ,
                        "foliageplus:rustle", 8.0F, pitch);
                serverSoundCooldown.put(player, Integer.valueOf(2 + world.rand.nextInt(2)));
                soundPlayed = true;
            }
        }

        FoliagePlusHooks.recordContactTransition(
                player,
                world,
                inLeaves,
                moving,
                hasSpeedModifier(player),
                soundPlayed);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.entityLiving == null) {
            return;
        }

        World world = event.entityLiving.worldObj;
        if (world != null && world.isRemote) {
            FoliagePlusHooks.clearDebugState(event.entityLiving);
            return;
        }

        clearEntityState(event.entityLiving);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerState(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearPlayerState(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayerState(event.player);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.world;
        FoliagePlusHooks.clearDebugState(world);

        if (world == null || world.isRemote) {
            return;
        }

        Iterator<Map.Entry<EntityLivingBase, Double>> speedIterator = currentSpeedMultipliers.entrySet().iterator();
        while (speedIterator.hasNext()) {
            EntityLivingBase living = speedIterator.next().getKey();
            if (living == null || living.worldObj == world) {
                if (living != null) {
                    removeSpeedModifier(living);
                }
                speedIterator.remove();
            }
        }

        removePlayersFromWorld(serverSoundCooldown, world);
        removePlayersFromWorld(serverPlayerPositions, world);
    }

    private void updateSpeedModifierSmooth(EntityLivingBase living, double targetMultiplier) {
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) {
            currentSpeedMultipliers.remove(living);
            return;
        }

        // Never retain a foliage penalty after contact ends, including teleports
        // and block removal. Smooth entry only; the old exit ramp lasted up to
        // 17 ticks and felt like random slowdown well away from the leaves.
        if (Double.isNaN(targetMultiplier) || Double.isInfinite(targetMultiplier) || targetMultiplier >= 0.999D) {
            clearSpeedState(living);
            return;
        }
        if (targetMultiplier < 0.01D) targetMultiplier = 0.01D;

        Double currentObject = currentSpeedMultipliers.get(living);

        double currentMultiplier = currentObject != null
                ? currentObject.doubleValue()
                : getCurrentMultiplier(attr);
        if (Double.isNaN(currentMultiplier) || Double.isInfinite(currentMultiplier)) {
            currentMultiplier = 1.0D;
        }

        if (Math.abs(currentMultiplier - targetMultiplier) <= SPEED_SMOOTH_STEP) {
            currentMultiplier = targetMultiplier;
        } else if (currentMultiplier < targetMultiplier) {
            currentMultiplier += SPEED_SMOOTH_STEP;
        } else {
            currentMultiplier -= SPEED_SMOOTH_STEP;
        }

        applySpeedModifier(living, currentMultiplier);
        currentSpeedMultipliers.put(living, Double.valueOf(currentMultiplier));
    }

    private double getCurrentMultiplier(IAttributeInstance attr) {
        AttributeModifier existing = attr.getModifier(SPEED_MOD_UUID);
        if (existing == null || existing.getOperation() != 2) {
            return 1.0D;
        }
        return 1.0D + existing.getAmount();
    }

    private void applySpeedModifier(EntityLivingBase living, double multiplier) {
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) {
            return;
        }

        if (multiplier < 0.01D) multiplier = 0.01D;
        if (multiplier > 1.00D) multiplier = 1.00D;

        AttributeModifier existing = attr.getModifier(SPEED_MOD_UUID);
        double amount = multiplier - 1.0D;

        if (existing != null) {
            if (existing.getOperation() == 2 && !existing.isSaved()
                    && Math.abs(existing.getAmount() - amount) < 0.000001D) {
                return;
            }
            attr.removeModifier(existing);
        }

        if (multiplier >= 0.999D) {
            return;
        }

        AttributeModifier modifier = new AttributeModifier(SPEED_MOD_UUID, SPEED_MOD_NAME, amount, 2);
        modifier.setSaved(false);
        attr.applyModifier(modifier);
    }

    private boolean hasSpeedModifier(EntityLivingBase living) {
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        return attr != null && attr.getModifier(SPEED_MOD_UUID) != null;
    }

    private void clearSpeedState(EntityLivingBase living) {
        currentSpeedMultipliers.remove(living);
        removeSpeedModifier(living);
    }

    private void clearPlayerState(EntityPlayer player) {
        if (player == null) {
            return;
        }

        World world = player.worldObj;
        if (world != null && world.isRemote) {
            FoliagePlusHooks.clearDebugState(player);
            return;
        }

        clearEntityState(player);
    }

    private void clearEntityState(EntityLivingBase living) {
        if (living == null) {
            return;
        }
        clearSpeedState(living);
        if (living instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) living;
            serverSoundCooldown.remove(player);
            serverPlayerPositions.remove(player);
        }
        FoliagePlusHooks.clearDebugState(living);
    }

    private void removeSpeedModifier(EntityLivingBase living) {
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) {
            return;
        }

        AttributeModifier existing = attr.getModifier(SPEED_MOD_UUID);
        if (existing != null) {
            attr.removeModifier(existing);
        }
    }

    private boolean updateAndCheckServerMovement(EntityPlayer player) {
        PlayerPosition previous = serverPlayerPositions.get(player);
        if (previous == null || previous.world != player.worldObj) {
            serverPlayerPositions.put(player, new PlayerPosition(player.worldObj, player.posX, player.posZ));
            return false;
        }

        double deltaX = player.posX - previous.x;
        double deltaZ = player.posZ - previous.z;
        previous.x = player.posX;
        previous.z = player.posZ;

        return (deltaX * deltaX) + (deltaZ * deltaZ) >= SOUND_MOVEMENT_THRESHOLD_SQUARED;
    }

    private boolean isMovingHorizontally(EntityLivingBase living) {
        double speedSquared = (living.motionX * living.motionX) + (living.motionZ * living.motionZ);
        return speedSquared >= SOUND_MOVEMENT_THRESHOLD_SQUARED;
    }

    private <T> void removePlayersFromWorld(Map<EntityPlayer, T> map, World world) {
        Iterator<EntityPlayer> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            EntityPlayer player = iterator.next();
            if (player == null || player.worldObj == world) {
                iterator.remove();
            }
        }
    }

    private static final class PlayerPosition {
        private final World world;
        private double x;
        private double z;

        private PlayerPosition(World world, double x, double z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
    }
}
