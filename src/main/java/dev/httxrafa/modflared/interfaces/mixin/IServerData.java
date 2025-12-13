package dev.httxrafa.modflared.interfaces.mixin;

import dev.httxrafa.modflared.tunnel.TunnelStatus;

public interface IServerData {

    void setTunnelStatus(TunnelStatus status);
    TunnelStatus getTunnelStatus();

}
