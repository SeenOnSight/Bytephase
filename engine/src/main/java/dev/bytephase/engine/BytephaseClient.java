package dev.bytephase.engine;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.donutsmp.GamblingRigger;
import dev.bytephase.engine.config.ClientFonts;
import dev.bytephase.engine.entity.ClientThreadUtils;
import dev.bytephase.engine.gui.AcBanner;
import dev.bytephase.engine.gui.DisabledEnabled;
import dev.bytephase.engine.render.BlockStateHelper;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import dev.bytephase.engine.render.FillDraw;
import dev.bytephase.engine.render.FrustumUtils;
import dev.bytephase.engine.render.GhostBuilderUtils;
import dev.bytephase.engine.render.QueueCommand;
import dev.bytephase.engine.render.VertexTextureFont;
import dev.bytephase.engine.utils.ConnectionTimeUtils;
import dev.bytephase.engine.utils.DirExtractionUtils;
import dev.bytephase.engine.utils.EntityDamageUtils;
import dev.bytephase.engine.utils.InventoryPlayerUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
/** Runtime services for imported features; Bytephase owns the menu and config. */
public final class BytephaseClient implements ClientModInitializer {
    public static ClientFonts fontManager;
    public static VertexTextureFont fontRenderer() { return fontManager.getLargeFont(); }
    @Override public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BlockStateHelper.updateHashOpen();
            if (ModuleRegistry.getModule(AutoBuilder.class) instanceof AutoBuilder builder) builder.leaveMultiplayerSession();
            ConnectionTimeUtils.update2();
            EntityDamageUtils.updateTime();
            ClientThreadUtils.updateDequeFlag();
            InventoryPlayerUtils.updateTimeSelected();
        });
        WorldRenderEvents.END_EXTRACTION.register(context -> FrustumUtils.handleFrustum(context.frustum()));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!dev.bytephase.module.engine.BytephaseIntegration.ready()
                || DirExtractionUtils.isFlag() || dev.bytephase.render.capture.StreamProof.isCleanFrame()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            QueueCommand event = new QueueCommand(context.matrices(), new Matrix4f(), client.getRenderTickCounter().getTickProgress(false));
            event.commandQueue = context.commandQueue();
            ModuleRegistry.dispatchRender3D(event);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof Generic3x3ContainerScreen container && ModuleRegistry.getModule(GamblingRigger.class) instanceof GamblingRigger gambling) {
                FillDraw draw = new FillDraw(container, gambling);
                ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, delta) -> draw.renderGamblingRigger(context, mouseX, mouseY));
                ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> !draw.canGamblingRigger(click.x(), click.y(), click.button()));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> ModuleRegistry.onEndClientTick());
        HudElementRegistry.addLast(Identifier.of("bytephase", "imported-notifications"), (context, counter) -> {
            if (!dev.bytephase.module.engine.BytephaseIntegration.ready()
                || DirExtractionUtils.isFlag() || dev.bytephase.render.capture.StreamProof.isCleanFrame()) return;
            dev.bytephase.engine.render.MatrixEspUtils.updateFlagTime();
            fontManager.setMatrixStack(null);
            if (!DisabledEnabled.getInstance().hasPending() && !AcBanner.hasPending()) return;
            BytephaseRoundedUtils.update();
            try {
                DisabledEnabled.getInstance().renderScaledWindow(context);
                AcBanner.renderScaledWindow(context);
            } finally { BytephaseRoundedUtils.updateBufferCloseCount(); }
        });
    }
}
