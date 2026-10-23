package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import com.wdg.foliageplus.core.FoliagePlusHooks;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.wdg.foliageplus.RegressionAssertions.*;

/** Real Forge/Minecraft attributes and events; no game loop or GL context needed. */
public class FoliageStabilityTest {
    private static final UUID FOLIAGE = UUID.fromString("b6a1e51f-6b44-4b5f-b97f-0a6a56c63571");
    private static final Unsafe UNSAFE = unsafe();
    private static int nextEntityId;
    private TestWorld world;
    private EntityPlayerMP player;
    private LeafBlock leaves;
    private FoliagePlusTickHandler handler;

    public void setup() throws Exception {
        FoliagePlusConfig.enabled = true;
        FoliagePlusConfig.allowFullCover = true;
        FoliagePlusConfig.affectAllEntities = false;
        FoliagePlusConfig.speedMultiplier = 0.5D;
        FoliagePlusConfig.debugContactTransitions = false;
        FoliagePlusConfig.setServerSettings(null);
        FoliagePlusHooks.applyConfigSnapshot();
        world = newWorld(false);
        player = newEntity(EntityPlayerMP.class, world);
        setField(player, "capabilities", new PlayerCapabilities());
        leaves = new LeafBlock();
        world.put(0, 64, 0, leaves);
        handler = new FoliagePlusTickHandler();
    }

    public void test_invisibleNtmBeamNeverCountsAsFoliage() {
        Block beam = new PretendLeaves(Material.air);
        beam.setBlockBounds(0,0,0,0,0,0);
        world.put(0, 64, 0, beam);
        // Reproduce the NTM zero-volume beam point inside a player's body.
        player.boundingBox.setBounds(-.3,63.8,-.3,.3,65.6,.3);
        assertFalse(contact());
        assertFalse(skip(world.getBlock(0, 64, 0), 0, 64, 0));
        tick(20);
        assertNull(modifier());
        assertEquals(0, world.sounds);
    }

    public void test_etFuturumShroomlightAndWartRemainSolid() {
        for (Material material : new Material[] {Material.gourd, Material.grass}) {
            Block block = new PretendLeaves(material);
            world.put(0, 64, 0, block);
            assertFalse(contact());
            assertFalse(skip(block, 0, 64, 0));
        }
    }

    public void test_genericMaterialLeavesSupportEtFuturumParityBlocks() {
        assertTrue(contact());
        assertTrue(skip(leaves, 0, 64, 0));
    }

    public void test_contactIgnoresAndDoesNotMutateSingletonBounds() {
        leaves.setBlockBounds(10, 10, 10, 11, 11, 11);
        assertTrue(contact());
        assertEquals(10, leaves.getBlockBoundsMinX(), 0);
        leaves.setBlockBounds(0, 0, 0, 0, 0, 0);
        assertTrue(contact());
        assertEquals(0, leaves.getBlockBoundsMaxX(), 0);
    }

    public void test_touchingFaceEdgeFloorAndCeilingDoNotSlow() {
        for (double[] b : new double[][] {
                {1,64,.2,1.6,65.8,.8}, {.2,65,.2,.8,66.8,.8},
                {.2,62.2,.2,.8,64,.8}, {1,64,1,1.6,65.8,1.6},
                {.999,64,.2,1.599,65.8,.8}}) {
            player.boundingBox.setBounds(b[0],b[1],b[2],b[3],b[4],b[5]);
            assertFalse(contact());
        }
        player.boundingBox.setBounds(.98,64,.2,1.58,65.8,.8);
        assertTrue(contact());
    }

    public void test_negativeCoordinatesAndWorldHeightEdgesWork() {
        world.put(-1, 255, -1, leaves);
        player.boundingBox.setBounds(-.8,255,-.8,-.2,256.8,-.2);
        assertTrue(contact());
        world.put(-1, 0, -1, leaves);
        player.boundingBox.setBounds(-.8,-1,-.8,-.2,.5,-.2);
        assertTrue(contact());
    }

    public void test_unloadedChunksAreNeverReadOrGenerated() {
        world.loaded = false;
        world.reads = 0;
        assertFalse(contact());
        assertEquals(0, world.reads);
    }

    public void test_invalidHugeAndWrongWorldBoundsAreRejected() throws Exception {
        assertFalse(FoliagePlusHooks.isEntityInLeaves(player, newWorld(false)));
        player.boundingBox.setBounds(Double.NaN,64,0,1,66,1);
        assertFalse(contact());
        player.boundingBox.setBounds(-10000,64,-10000,10000,66,10000);
        world.reads = 0;
        assertFalse(contact());
        assertEquals(0, world.reads);
    }

    public void test_fullCoverRestrictionMatchesContactAndCollision() {
        FoliagePlusConfig.allowFullCover = false;
        FoliagePlusHooks.applyConfigSnapshot();
        world.put(0, 65, 0, leaves);
        player.boundingBox.setBounds(.2,64,.2,.8,64.9,.8);
        assertFalse(skip(leaves, 0, 64, 0));
        assertFalse(contact());
        player.boundingBox.setBounds(.2,65,.2,.8,66.8,.8);
        assertTrue(contact());
        world.put(0, 65, 0, new PretendLeaves(Material.air));
        assertTrue(skip(leaves, 0, 64, 0));
    }

    public void test_nonPlayerToggleMatchesCollisionAndSlowdown() throws Exception {
        EntityPig pig = newEntity(EntityPig.class, world);
        assertFalse(FoliagePlusHooks.isEntityInLeaves(pig, world));
        assertFalse(FoliagePlusHooks.shouldSkipCollision(leaves, world, 0,64,0,pig));
        FoliagePlusConfig.affectAllEntities = true;
        FoliagePlusHooks.applyConfigSnapshot();
        assertTrue(FoliagePlusHooks.isEntityInLeaves(pig, world));
        handler.onLivingUpdate(new LivingEvent.LivingUpdateEvent(pig));
        assertNotNull(speed(pig).getModifier(FOLIAGE));
        FoliagePlusConfig.affectAllEntities = false;
        FoliagePlusHooks.applyConfigSnapshot();
        handler.onLivingUpdate(new LivingEvent.LivingUpdateEvent(pig));
        assertNull(speed(pig).getModifier(FOLIAGE));
    }

    public void test_exitTeleportAndRemovedLeavesClearImmediately() {
        tick(20);
        assertEquals(-.5, modifier().getAmount(), .000001);
        player.boundingBox.setBounds(20,64,20,20.6,65.8,20.6);
        tick(1);
        assertNull(modifier());
        player.boundingBox.setBounds(.2,64,.2,.8,65.8,.8);
        tick(20);
        world.blocks.clear();
        tick(1);
        assertNull(modifier());
    }

    public void test_disablingOrNormalSpeedClearsImmediately() {
        tick(20);
        FoliagePlusConfig.enabled = false;
        FoliagePlusHooks.applyConfigSnapshot();
        tick(1);
        assertNull(modifier());
        assertFalse(skip(leaves, 0,64,0));
        FoliagePlusConfig.enabled = true;
        FoliagePlusHooks.applyConfigSnapshot();
        tick(20);
        FoliagePlusConfig.speedMultiplier = 1;
        FoliagePlusHooks.applyConfigSnapshot();
        tick(1);
        assertNull(modifier());
    }

    public void test_flightNoClipAndMountsDoNotRetainPenalty() throws Exception {
        tick(20);
        player.capabilities.isFlying = true;
        tick(1);
        assertNull(modifier());
        player.capabilities.isFlying = false;
        tick(20);
        player.noClip = true;
        tick(1);
        assertNull(modifier());
        player.noClip = false;
        tick(20);
        player.ridingEntity = newEntity(EntityPig.class, world);
        tick(1);
        assertNull(modifier());
    }

    public void test_ntmArmorSprintAndPowderSnowModifiersArePreserved() {
        UUID armor = UUID.randomUUID(), frost = UUID.randomUUID(), sprint = UUID.randomUUID();
        IAttributeInstance attr = speed(player);
        attr.applyModifier(new AttributeModifier(armor, "NTM armor", .1, 0));
        attr.applyModifier(new AttributeModifier(frost, "Powder snow frost", -.05, 0));
        attr.applyModifier(new AttributeModifier(sprint, "Sprint", .3, 2));
        double external = attr.getAttributeValue();
        tick(20);
        assertEquals(external * .5, attr.getAttributeValue(), .000001);
        assertFalse(modifier().isSaved());
        world.blocks.clear();
        tick(1);
        assertEquals(external, attr.getAttributeValue(), .000001);
        assertEquals(.1, attr.getBaseValue(), .000001);
        assertNotNull(attr.getModifier(armor));
        assertNotNull(attr.getModifier(frost));
        assertNotNull(attr.getModifier(sprint));
    }

    public void test_staleSavedPenaltyIsRemovedOutsideLeaves() {
        world.blocks.clear();
        speed(player).applyModifier(new AttributeModifier(FOLIAGE, "old foliage", -.8, 2));
        tick(1);
        assertNull(modifier());
    }

    public void test_lifecycleEventsRemoveOnlyOwnedModifier() {
        tick(20);
        handler.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertNull(modifier());
        tick(20);
        handler.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player));
        assertNull(modifier());
        tick(20);
        handler.onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(player,0,1));
        assertNull(modifier());
        tick(20);
        handler.onLivingDeath(new LivingDeathEvent(player, net.minecraft.util.DamageSource.generic));
        assertNull(modifier());
        tick(20);
        handler.onWorldUnload(new WorldEvent.Unload(world));
        assertNull(modifier());
    }

    public void test_clientTicksNeverChangeServerOwnedSpeed() throws Exception {
        TestWorld clientWorld = newWorld(true);
        EntityPlayerMP client = newEntity(EntityPlayerMP.class, clientWorld);
        setField(client, "entityUniqueID", player.getUniqueID());
        tick(20);
        handler.onLivingUpdate(new LivingEvent.LivingUpdateEvent(client));
        handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, client));
        assertNull(speed(client).getModifier(FOLIAGE));
        assertEquals(-.5, modifier().getAmount(), .000001);
    }

    public void test_playerLivingAndStartEventsDoNotDoubleApplySmoothing() {
        handler.onLivingUpdate(new LivingEvent.LivingUpdateEvent(player));
        handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        assertNull(modifier());
        tick(1);
        assertEquals(-.06, modifier().getAmount(), .000001);
    }

    public void test_rustleUsesOneServerMovementSampleAndCooldown() {
        tick(1);
        assertEquals(0, world.sounds);
        player.posX += .05;
        tick(1);
        assertEquals(1, world.sounds);
        player.posX += .05;
        tick(1);
        assertEquals(1, world.sounds);
        world.blocks.clear();
        player.posX += .05;
        tick(1);
        assertEquals(1, world.sounds);
    }

    public void test_collisionWrapperHonorsSubclassOverridesAndNullQueries() {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        FoliagePlusHooks.addCollisionBoxes(leaves,world,0,64,0,player.boundingBox,boxes,player);
        assertTrue(boxes.isEmpty());
        FoliagePlusHooks.addCollisionBoxes(leaves,world,0,64,0,player.boundingBox,boxes,null);
        assertEquals(1, boxes.size());
        world.put(0,64,0,new PretendLeaves(Material.gourd));
        FoliagePlusHooks.addCollisionBoxes(world.getBlock(0,64,0),world,0,64,0,player.boundingBox,boxes,player);
        assertEquals(2, boxes.size());
    }

    public void test_serverSettingsOverrideOnlyClientWorldAndResetCleanly() throws Exception {
        TestWorld client = newWorld(true);
        FoliagePlusConfig.Settings server = new FoliagePlusConfig.Settings(false,false,true,.2);
        FoliagePlusConfig.setServerSettings(server);
        assertFalse(FoliagePlusConfig.getSettings(client).enabled);
        assertTrue(FoliagePlusConfig.getSettings(world).enabled);
        FoliagePlusConfig.setServerSettings(null);
        assertTrue(FoliagePlusConfig.getSettings(client).enabled);
    }

    public void test_settingsPacketRoundTripsAndRejectsInvalidSpeed() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        try {
            new FoliagePlusNetwork.SettingsMessage(new FoliagePlusConfig.Settings(false,true,true,.23)).toBytes(buf);
            FoliagePlusNetwork.SettingsMessage received = new FoliagePlusNetwork.SettingsMessage();
            received.fromBytes(buf);
            Field field = received.getClass().getDeclaredField("settings");
            field.setAccessible(true);
            FoliagePlusConfig.Settings settings = (FoliagePlusConfig.Settings) field.get(received);
            assertFalse(settings.enabled);
            assertTrue(settings.allowFullCover);
            assertTrue(settings.affectAllEntities);
            assertEquals(.23,settings.speedMultiplier,0);
            assertEquals(1,new FoliagePlusConfig.Settings(true,true,false,Double.NaN).speedMultiplier,0);
            assertEquals(1,new FoliagePlusConfig.Settings(true,true,false,Double.NEGATIVE_INFINITY).speedMultiplier,0);
            assertEquals(.01,new FoliagePlusConfig.Settings(true,true,false,-10).speedMultiplier,0);
        } finally { buf.release(); }
    }

    private boolean contact() { return FoliagePlusHooks.isEntityInLeaves(player,world); }
    private boolean skip(Block block,int x,int y,int z) { return FoliagePlusHooks.shouldSkipCollision(block,world,x,y,z,player); }
    private void tick(int count) {
        for (int i=0;i<count;i++) handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
    }
    private AttributeModifier modifier() { return speed(player).getModifier(FOLIAGE); }
    private static IAttributeInstance speed(EntityLivingBase entity) {
        return entity.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
    }

    // Allocate test worlds/entities without opening saves, chunks, network
    // connections, or a Minecraft singleton. Gameplay logic uses real methods.
    private static TestWorld newWorld(boolean remote) throws Exception {
        TestWorld world = (TestWorld) UNSAFE.allocateInstance(TestWorld.class);
        world.blocks = new HashMap<String,Block>();
        world.loaded = true;
        world.isRemote = remote;
        setField(world,"provider",new WorldProviderSurface());
        setField(world,"rand",new Random(1));
        return world;
    }

    private static <T extends EntityLivingBase> T newEntity(Class<T> type, World world) throws Exception {
        T entity = type.cast(UNSAFE.allocateInstance(type));
        entity.setEntityId(++nextEntityId);
        entity.worldObj = world;
        entity.posX = .5; entity.posY = 64; entity.posZ = .5;
        setField(entity,"boundingBox",AxisAlignedBB.getBoundingBox(.2,64,.2,.8,65.8,.8));
        setField(entity,"entityUniqueID",UUID.randomUUID());
        DataWatcher watcher = new DataWatcher(entity);
        watcher.addObject(6,Float.valueOf(20));
        setField(entity,"dataWatcher",watcher);
        entity.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
        return entity;
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    static void setField(Object target,String name,Object value) throws Exception {
        Class<?> type=target.getClass();
        while (type != null) {
            try {
                Field field=type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target,value);
                return;
            } catch (NoSuchFieldException e) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static class LeafBlock extends Block {
        LeafBlock() { super(Material.leaves); }
        @Override @SuppressWarnings("unchecked")
        public void addCollisionBoxesToList(World w,int x,int y,int z,AxisAlignedBB mask,List boxes,Entity e) {
            boxes.add(AxisAlignedBB.getBoundingBox(x,y,z,x+1,y+1,z+1));
        }
    }
    private static final class PretendLeaves extends Block {
        PretendLeaves(Material material) { super(material); }
        @Override public boolean isLeaves(IBlockAccess w,int x,int y,int z) { return true; }
    }

    private static final class TestWorld extends World {
        Map<String,Block> blocks;
        boolean loaded;
        int reads, sounds;
        private TestWorld() { super(null,"foliage-test",(WorldSettings)null,null,null); }
        void put(int x,int y,int z,Block block) { blocks.put(x+":"+y+":"+z,block); }
        @Override public boolean blockExists(int x,int y,int z) { return loaded && y>=0 && y<256; }
        @Override public Block getBlock(int x,int y,int z) {
            if (!loaded) throw new AssertionError("Read unloaded chunk");
            reads++;
            return blocks.get(x+":"+y+":"+z);
        }
        @Override public int getBlockMetadata(int x,int y,int z) { return 0; }
        @Override public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch) { sounds++; }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }
}
