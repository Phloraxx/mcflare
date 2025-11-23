package de.rafael.modflared.interfaces.mixin;

import de.rafael.modflared.tunnel.TunnelStatus;

public interface IServerData {

    void setTunnelStatus(TunnelStatus status);
    TunnelStatus getTunnelStatus();

}
