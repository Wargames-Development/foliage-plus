package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.world.WorldServer;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static com.wdg.foliageplus.FoliageStabilityTest.setField;
import static com.wdg.foliageplus.RegressionAssertions.*;

public class FoliageNetworkTest {
    private static final Unsafe UNSAFE = unsafe();
    private FoliagePlusNetwork network;
    private CapturingChannel channel;
    private Map<NetHandlerPlayServer,FoliagePlusConfig.Settings> clients;
    private NetHandlerPlayServer connection;
    private EntityPlayerMP player;
    private NetworkManager manager;

    public void setup() throws Exception {
        FoliagePlusConfig.enabled = true;
        FoliagePlusConfig.applyLocalSnapshot();
        FoliagePlusConfig.setServerSettings(null);
        network = (FoliagePlusNetwork) UNSAFE.allocateInstance(FoliagePlusNetwork.class);
        channel = (CapturingChannel) UNSAFE.allocateInstance(CapturingChannel.class);
        clients = new WeakHashMap<NetHandlerPlayServer,FoliagePlusConfig.Settings>();
        setField(network,"channel",channel);
        setField(network,"clients",clients);
        connection = (NetHandlerPlayServer) UNSAFE.allocateInstance(NetHandlerPlayServer.class);
        player = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        setField(connection,"playerEntity",player);
        manager = manager(connection);
        setField(connection,"netManager",manager);
    }

    public void test_registrationSurvivesLongForgeHandshakeThenSendsOnce() {
        register("REGISTER");
        tick(); tick(); tick();
        assertEquals(1,clients.size());
        assertEquals(0,channel.sent);
        player.playerNetServerHandler = connection;
        tick(); tick();
        assertEquals(1,channel.sent);
        FoliagePlusConfig.enabled = false;
        FoliagePlusConfig.applyLocalSnapshot();
        tick(); tick();
        assertEquals(2,channel.sent);
    }

    public void test_oldClientsReceiveNoPacketsAndUnregisterStopsSync() {
        player.playerNetServerHandler = connection;
        tick();
        assertEquals(0,channel.sent);
        register("REGISTER");
        tick();
        assertEquals(1,channel.sent);
        register("UNREGISTER");
        FoliagePlusConfig.applyLocalSnapshot();
        tick();
        assertEquals(1,channel.sent);
        assertEquals(0,clients.size());
    }

    public void test_respawnKeepsConnectionSubscriptionAndLogoutCleansIt() throws Exception {
        player.playerNetServerHandler = connection;
        register("REGISTER"); tick();
        EntityPlayerMP replacement = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        replacement.playerNetServerHandler = connection;
        connection.playerEntity = replacement;
        network.onRespawn(new PlayerEvent.PlayerRespawnEvent(replacement));
        tick();
        assertEquals(2,channel.sent);
        network.onLogout(new PlayerEvent.PlayerLoggedOutEvent(replacement));
        assertEquals(0,clients.size());
    }

    public void test_closedConnectionsAreDiscardedBeforeSending() {
        register("REGISTER");
        manager.channel().close();
        tick();
        assertEquals(0,clients.size());
        assertEquals(0,channel.sent);
    }

    public void test_clientConnectionIsolationRejectsLatePacketsAndDisconnects() throws Exception {
        INetHandlerPlayClient first = clientHandler();
        NetworkManager firstManager = manager(first);
        network.onConnect(new FMLNetworkEvent.ClientConnectedToServerEvent(firstManager,"MODDED"));
        WorldServer clientWorld = (WorldServer) UNSAFE.allocateInstance(WorldServer.class);
        clientWorld.isRemote = true;
        FoliagePlusNetwork.SettingsMessage message = new FoliagePlusNetwork.SettingsMessage(
                new FoliagePlusConfig.Settings(false,false,true,.2));
        FoliagePlusNetwork.SettingsHandler handler = new FoliagePlusNetwork.SettingsHandler();
        handler.onMessage(message,context(first,Side.CLIENT));
        assertFalse(FoliagePlusConfig.getSettings(clientWorld).enabled);
        assertTrue(FoliagePlusConfig.getSettings(null).enabled);

        INetHandlerPlayClient second = clientHandler();
        NetworkManager secondManager = manager(second);
        network.onConnect(new FMLNetworkEvent.ClientConnectedToServerEvent(secondManager,"MODDED"));
        handler.onMessage(message,context(first,Side.CLIENT));
        assertTrue(FoliagePlusConfig.getSettings(clientWorld).enabled);
        handler.onMessage(message,context(second,Side.CLIENT));
        network.onDisconnect(new FMLNetworkEvent.ClientDisconnectionFromServerEvent(firstManager));
        assertFalse(FoliagePlusConfig.getSettings(clientWorld).enabled);
        network.onDisconnect(new FMLNetworkEvent.ClientDisconnectionFromServerEvent(secondManager));
        assertTrue(FoliagePlusConfig.getSettings(clientWorld).enabled);
        handler.onMessage(message,context(second,Side.CLIENT));
        assertTrue(FoliagePlusConfig.getSettings(clientWorld).enabled);
        firstManager.channel().close();
        secondManager.channel().close();
    }

    private void tick() { network.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END)); }
    private void register(String operation) {
        network.onRegistration(new FMLNetworkEvent.CustomPacketRegistrationEvent<NetHandlerPlayServer>(manager,
                Collections.singleton("foliageplus_cfg1"),operation,Side.SERVER,NetHandlerPlayServer.class));
    }
    private static MessageContext context(INetHandler handler,Side side) throws Exception {
        Constructor<MessageContext> constructor = MessageContext.class.getDeclaredConstructor(INetHandler.class,Side.class);
        constructor.setAccessible(true);
        return constructor.newInstance(handler,side);
    }
    private static NetworkManager manager(INetHandler handler) throws Exception {
        NetworkManager manager = new NetworkManager(false);
        setField(manager,"channel",new EmbeddedChannel(new ChannelInboundHandlerAdapter()));
        manager.setNetHandler(handler);
        return manager;
    }
    private static INetHandlerPlayClient clientHandler() {
        return (INetHandlerPlayClient) Proxy.newProxyInstance(FoliageNetworkTest.class.getClassLoader(),
                new Class<?>[] {INetHandlerPlayClient.class},(proxy,method,args) -> null);
    }
    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static final class CapturingChannel extends SimpleNetworkWrapper {
        int sent;
        private CapturingChannel() { super("unused-test"); }
        @Override public void sendTo(IMessage message,EntityPlayerMP player) { sent++; }
    }
}
