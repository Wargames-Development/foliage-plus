package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import com.wdg.foliageplus.core.FoliagePlusHooks;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

@Mod(
        modid = FoliagePlusMod.MODID,
        name = FoliagePlusMod.NAME,
        version = FoliagePlusMod.VERSION,
        acceptableRemoteVersions = "*"
)
public class FoliagePlusMod {

    public static final String MODID = "foliageplus";
    public static final String NAME = "Foliage+";
    public static final String VERSION = "@VERSION@";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load config (server + client)
        FoliagePlusConfig.load(event.getModConfigurationDirectory());

        // Push initial values into hook class (used by ASM transformer)
        FoliagePlusHooks.applyConfigSnapshot();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Tick events (one instance only)
        FoliagePlusTickHandler handler = new FoliagePlusTickHandler();
        FMLCommonHandler.instance().bus().register(handler);
    }
}
