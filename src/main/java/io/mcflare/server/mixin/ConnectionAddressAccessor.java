package io.mcflare.server.mixin;

import java.net.SocketAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAddressAccessor {
    @Accessor("address")
    void mcflare$setAddress(SocketAddress address);
}
