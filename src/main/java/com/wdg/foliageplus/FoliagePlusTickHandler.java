package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import com.wdg.foliageplus.core.FoliagePlusHooks;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FoliagePlusTickHandler {

    private static final UUID SPEED_MOD_UUID = UUID.fromString("b6a1e51f-6b44-4b5f-b97f-0a6a56c63571");
    private static final String SPEED_MOD_NAME = "FoliagePlusSlow";

    private final Map<UUID, Integer> clientSoundCooldown = new HashMap<UUID, Integer>();

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving == null) {
            return;
        }

        if (!FoliagePlusConfig.enabled) {
            // If disabled, make sure we remove any leftover speed modifiers
            removeSpeedModifier(event.entityLiving);
            return;
        }

        EntityLivingBase living = event.entityLiving;
        World world = living.worldObj;
        if (world == null) {
            return;
        }

        boolean isPlayer = (living instanceof EntityPlayer);

        if (!FoliagePlusConfig.affectAllEntities && !isPlayer) {
            // Players-only mode: don't affect mobs
            removeSpeedModifier(living);
            return;
        }

        if (isPlayer) {
            EntityPlayer player = (EntityPlayer) living;
            if (player.capabilities.isFlying) {
                removeSpeedModifier(living);
                return;
            }
        }

        boolean inLeaves = FoliagePlusHooks.isEntityInLeaves(living, world);
        if (!inLeaves) {
            removeSpeedModifier(living);
            return;
        }

        applySpeedModifier(living, FoliagePlusConfig.speedMultiplier);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        final EntityPlayer player = event.player;
        final World world = player.worldObj;

        if (!FoliagePlusConfig.enabled) {
            return;
        }

        // Client-only: play a vanilla rustle sound while moving through leaves
        if (world.isRemote) {
            handleClientSound(player);
        }
    }

    private void applySpeedModifier(EntityLivingBase living, double multiplier) {
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) {
            return;
        }

        // Clamp 0.01 - 1.00
        if (multiplier < 0.01D) multiplier = 0.01D;
        if (multiplier > 1.00D) multiplier = 1.00D;

        AttributeModifier existing = attr.getModifier(SPEED_MOD_UUID);

        // If multiplier is basically normal speed, remove modifier
        if (multiplier >= 0.999D) {
            if (existing != null) {
                attr.removeModifier(existing);
            }
            return;
        }

        double amount = multiplier - 1.0D; // negative value, operation 2 => multiplicative
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 0.000001D) {
                return; // already correct
            }
            attr.removeModifier(existing);
        }

        AttributeModifier mod = new AttributeModifier(SPEED_MOD_UUID, SPEED_MOD_NAME, amount, 2);
        mod.setSaved(false);
        attr.applyModifier(mod);
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

    private void handleClientSound(EntityPlayer player) {
        if (!FoliagePlusHooks.isEntityInLeaves(player, player.worldObj)) {
            return;
        }

        // Only play when actually moving
        final double speed2 = (player.motionX * player.motionX) + (player.motionZ * player.motionZ);
        if (speed2 < 0.0008D) {
            return;
        }

        UUID id = player.getUniqueID();
        int cd = clientSoundCooldown.containsKey(id) ? clientSoundCooldown.get(id) : 0;

        if (cd > 0) {
            clientSoundCooldown.put(id, cd - 1);
            return;
        }

        // Rustle sound: use World.playSound with distanceDelay=false (most reliable client path in 1.7.10)
        float pitch = 0.9F + (player.worldObj.rand.nextFloat() * 0.2F);
        // Play slightly above the player to reduce hard L/R panning artifacts while strafing
        double jx = player.posX + (player.worldObj.rand.nextDouble() - 0.5D) * 0.6D;
        double jz = player.posZ + (player.worldObj.rand.nextDouble() - 0.5D) * 0.6D;
        player.worldObj.playSound(player.posX, player.posY + 1.0D, player.posZ, "foliageplus:rustle", 8.0F, pitch, false);

        // Fallback (temporary): if you still hear nothing, this SHOULD be audible.
        // Remove once confirmed.
        //player.worldObj.playSound(player.posX, player.posY, player.posZ, "random.orb", 1.0F, 1.0F, false);


        // Shorter cooldown: makes it feel like consistent rustling while moving
        clientSoundCooldown.put(id, 2 + player.worldObj.rand.nextInt(2));
    }
}
