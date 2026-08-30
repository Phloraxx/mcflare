package io.mcflare.client.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import io.mcflare.client.McflareClient;
import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.client.tunnel.RunningTunnel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

/** Optional Simple Voice Chat integration loaded only when SVC asks for voicechat plugins. */
public final class McflareVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "mcflare";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatInitializationEvent.class, this::initializeSocket);
    }

    private void initializeSocket(ClientVoicechatInitializationEvent event) {
        RunningTunnel tunnel = currentTunnel();
        if (tunnel == null) return;

        String host = tunnel.access().hostname();
        event.setSocketImplementation(new McflareVoicechatSocket(host));
        McflareClient.LOGGER.info("Simple Voice Chat transport will be selected asynchronously for {}", host);
    }

    private static RunningTunnel currentTunnel() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) return null;
        Connection connection = listener.getConnection();
        return ((IConnection) connection).getRunningTunnel();
    }
}
