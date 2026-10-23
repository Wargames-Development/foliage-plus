package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional, versioned channel: older clients never receive an unknown packet. */
public final class FoliagePlusNetwork {
    private static final String CHANNEL_NAME = "foliageplus_cfg1";
    private final SimpleNetworkWrapper channel;
    private static final Object CLIENT_LOCK = new Object();
    private static INetHandler clientConnection;

    // Registration can arrive on Netty's thread; sends and entity reads run on
    // the server tick. Values track the last immutable local config publication.
    private final Map<NetHandlerPlayServer, FoliagePlusConfig.Settings> clients =
            new WeakHashMap<NetHandlerPlayServer, FoliagePlusConfig.Settings>();

    public FoliagePlusNetwork() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(SettingsHandler.class, SettingsMessage.class, 0, Side.CLIENT);
    }

    @SubscribeEvent
    public void onRegistration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (event.side != Side.SERVER || !(event.handler instanceof NetHandlerPlayServer)
                || !event.registrations.contains(CHANNEL_NAME)) {
            return;
        }
        NetHandlerPlayServer connection = (NetHandlerPlayServer) event.handler;
        synchronized (clients) {
            if ("REGISTER".equals(event.operation)) {
                clients.put(connection, null);
            } else if ("UNREGISTER".equals(event.operation)) {
                clients.remove(connection);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        FoliagePlusConfig.Settings settings = FoliagePlusConfig.getSettings(null);
        synchronized (clients) {
            Iterator<Map.Entry<NetHandlerPlayServer, FoliagePlusConfig.Settings>> iterator = clients.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<NetHandlerPlayServer, FoliagePlusConfig.Settings> entry = iterator.next();
                NetHandlerPlayServer connection = entry.getKey();
                if (connection == null || !connection.netManager.isChannelOpen()) {
                    iterator.remove();
                } else if (connection.playerEntity != null
                        && connection.playerEntity.playerNetServerHandler == connection && entry.getValue() != settings) {
                    // REGISTER is received during the Forge handshake, when
                    // playerNetServerHandler is deliberately null. Retain the
                    // subscription and wait for login to finish before sending.
                    channel.sendTo(new SettingsMessage(settings), connection.playerEntity);
                    entry.setValue(settings);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        synchronized (clients) {
            if (event.player instanceof EntityPlayerMP) {
                clients.remove(((EntityPlayerMP) event.player).playerNetServerHandler);
            }
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // The connection key survives Forge replacing the player entity.
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP replacement = (EntityPlayerMP) event.player;
        synchronized (clients) {
            NetHandlerPlayServer connection = replacement.playerNetServerHandler;
            if (clients.containsKey(connection)) clients.put(connection, null);
        }
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        synchronized (CLIENT_LOCK) {
            clientConnection = event.handler;
            FoliagePlusConfig.setServerSettings(null);
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        synchronized (CLIENT_LOCK) {
            if (clientConnection == event.handler) {
                clientConnection = null;
                FoliagePlusConfig.setServerSettings(null);
            }
        }
    }

    public static final class SettingsMessage implements IMessage {
        private FoliagePlusConfig.Settings settings;

        public SettingsMessage() {}

        public SettingsMessage(FoliagePlusConfig.Settings settings) {
            this.settings = settings;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            settings = new FoliagePlusConfig.Settings(buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readDouble());
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(settings.enabled);
            buf.writeBoolean(settings.allowFullCover);
            buf.writeBoolean(settings.affectAllEntities);
            buf.writeDouble(settings.speedMultiplier);
        }
    }

    public static final class SettingsHandler implements IMessageHandler<SettingsMessage, IMessage> {
        @Override
        public IMessage onMessage(SettingsMessage message, MessageContext context) {
            synchronized (CLIENT_LOCK) {
                if (context.side == Side.CLIENT && context.netHandler == clientConnection) {
                    // Publish data only. Never touch a client world/entity on Netty's thread.
                    FoliagePlusConfig.setServerSettings(message.settings);
                }
            }
            return null;
        }
    }
}
