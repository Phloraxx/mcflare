package de.rafael.modflared.mixin.client;

import de.rafael.modflared.Modflared;
import de.rafael.modflared.interfaces.mixin.IServerInfo;
import de.rafael.modflared.tunnel.TunnelStatus;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin extends MultiplayerServerListWidget.Entry {

    @Shadow @Final private ServerInfo server;

    @Unique
    private static final Identifier MODFLARED_INDICATOR_TEXTURE = Identifier.of(Modflared.MOD_ID, "icon/indicator");

    @Inject(method = "render", at = @At("TAIL"))
    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        var tunnelStatus = ((IServerInfo) server).getTunnelStatus();
        if(tunnelStatus != null && tunnelStatus.state() == TunnelStatus.State.USE) {
            int xOffset = this.getContentWidth() - 15;
            int yOffset = 10 + 1;
            int x = this.getContentX();
            int y = this.getContentY();
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, MODFLARED_INDICATOR_TEXTURE, x + xOffset, y + yOffset, 10, 10);

            // Tooltip
            int l = mouseX - x;
            int m = mouseY - y;
            if (l >= this.getContentWidth() - 15 && l <= this.getContentWidth() - 5 && m >= 9 && m <= 9 + 10) {
                context.drawTooltip(Text.translatable("gui.multiplayer.tunnel.status.0").formatted(Formatting.AQUA), mouseX, mouseY);
            }
        }
    }

}
