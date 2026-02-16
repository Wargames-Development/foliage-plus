package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import com.wdg.foliageplus.core.FoliagePlusHooks;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FoliagePlusTickHandler {

    private final Map<UUID, Integer> clientSoundCooldown = new HashMap<UUID, Integer>();

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

        if (!FoliagePlusConfig.affectAllEntities) {
            // Player-only mode: handled here; collisions are player-only too.
            handleEntity(player);
        } else {
            // All-entities mode:
            // - Collisions for ALL entities are handled by the ASM hook (Block.addCollisionBoxesToList).
            // - Slowdown is applied here for the player and also for riding entities on the server tick below.
            handleEntity(player);

            if (!world.isRemote && player.ridingEntity != null) {
                handleEntity(player.ridingEntity);
            }
        }

        // Client-only: play a vanilla rustle sound while moving through leaves
        if (world.isRemote) {
            handleClientSound(player);
        }
    }

    private void handleEntity(Entity entity) {
        if (entity == null) {
            return;
        }

        final World world = entity.worldObj;
        if (world == null) {
            return;
        }

        // Slowdown should be authoritative server-side.
        if (world.isRemote) {
            return;
        }

        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.capabilities.isFlying) {
                return;
            }
        }

        if (!FoliagePlusHooks.isEntityInLeaves(entity, world)) {
            return;
        }

        // Movement multiplier: 0.01 - 1.00
        final double mult = FoliagePlusConfig.speedMultiplier;

        entity.motionX *= mult;
        entity.motionZ *= mult;
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

        // Vanilla sound (generic foliage-ish rustle)
        player.worldObj.playSoundAtEntity(player, "step.grass", 0.25F, 1.0F);

        // Cooldown: about 0.5s to 1.0s depending on tickrate
        clientSoundCooldown.put(id, 10 + player.worldObj.rand.nextInt(11));
    }
}
