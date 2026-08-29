package io.mcflare.client.tunnel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public record TunnelStatus(RunningTunnel runningTunnel, State state) {

    public @Unmodifiable List<Component> generateFeedback() {
        return switch (state) {
            case USE -> List.of(
                    Component.translatable("gui.tunnel.status.use").withStyle(ChatFormatting.AQUA)
            );
            case DONT_USE -> List.of();
            case FAILED_TO_DETERMINE -> List.of(
                    Component.translatable("gui.tunnel.status.failed.0").withStyle(ChatFormatting.RED),
                    Component.translatable("gui.tunnel.status.failed.1").withStyle(ChatFormatting.RED),
                    Component.translatable("gui.tunnel.status.failed.2").withStyle(ChatFormatting.RED)
            );
        };
    }

    public enum State {
        USE,
        DONT_USE,
        FAILED_TO_DETERMINE
    }

}
