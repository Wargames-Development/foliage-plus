package com.wdg.foliageplus.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.Name("FoliagePlusCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({
        "com.wdg.foliageplus.core."
})
public class FoliagePlusCoreMod implements IFMLLoadingPlugin {

    static {
        System.out.println("[FoliagePlus] Coremod loaded (IFMLLoadingPlugin active).");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.wdg.foliageplus.core.FoliagePlusTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
