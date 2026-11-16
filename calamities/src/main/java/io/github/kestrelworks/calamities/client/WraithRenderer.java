package io.github.kestrelworks.calamities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kestrelworks.calamities.Calamities;
import io.github.kestrelworks.calamities.Wraith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/** Draws a spirit as a see-through, cold-tinted human figure that wavers in and out of sight. */
public class WraithRenderer extends LivingEntityRenderer<Wraith, WraithRenderer.State, HumanoidModel<WraithRenderer.State>> {

    public static class State extends HumanoidRenderState {
        Identifier texture = PALE;
        Identifier face = null;
        boolean slim;
        float alpha;
        /** 0 = pale figure only … 1 = its own face only */
        float reveal;
        /** the layer being submitted right now */
        float layerAlpha;
        int r = 200, g = 222, b = 255;
    }

    static final Identifier PALE = Calamities.id("textures/entity/wraith_pale.png");

    private final HumanoidModel<State> wide, slim;

    public WraithRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0f);
        this.wide = this.model;
        this.slim = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM));
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(Wraith e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        HumanoidMobRenderer.extractHumanoidRenderState(e, s, partial, this.itemModelResolver);
        s.walkAnimationSpeed = 0;
        ResolvableProfile prof = e.profile();
        boolean hasProfile = prof != null && prof != ResolvableProfile.Static.EMPTY && (prof.name().isPresent()
                || !prof.partialProfile().properties().isEmpty());
        boolean ready = true;
        s.texture = PALE;
        s.face = null;
        s.slim = false;
        s.reveal = 0;
        if (!e.pale() && hasProfile) {
            var cache = Minecraft.getInstance().playerSkinRenderCache();
            var found = cache.lookup(prof).getNow(null); // also starts fetching it while still pale
            float rv = Mth.clamp(e.reveal(), 0f, 1f);
            if (rv > 0.001f) {
                // stay unseen until its own face has arrived (never show the default skin first)
                if (found == null && e.tickCount < 100) ready = false;
                PlayerSkin skin = found != null && found.isPresent() ? found.get().playerSkin() : cache.getOrDefault(prof).playerSkin();
                s.face = skin.body().texturePath();
                s.slim = skin.model() == PlayerModelType.SLIM;
                s.reveal = rv;
            }
        }
        if (!ready) { s.alpha = 0; return; }
        float t = e.tickCount + partial;
        float waver = 0.82f + 0.18f * (float) (Math.sin(t * 0.23) * 0.6 + Math.sin(t * 0.61) * 0.4);
        // solid enough that the white figure doesn't take on the ground's colour
        s.alpha = Mth.clamp(e.visibility(partial) * 0.8f * waver, 0f, 1f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam) {
        if (s.alpha <= 0.02f) return;
        this.model = s.slim ? slim : wide;
        // the pale figure fades out while its own face fades in on the same body.
        // face is drawn over the pale one: face = r*A, pale = (1-r)*A / (1-r*A) keeps the whole figure at A (no dimmer midway)
        float rv = s.face == null ? 0f : s.reveal;
        if (rv < 0.99f) {
            s.texture = PALE;
            s.layerAlpha = Mth.clamp((1f - rv) * s.alpha / Math.max(0.05f, 1f - rv * s.alpha), 0f, 1f);
            s.r = 255; s.g = 255; s.b = 255;
            super.submit(s, pose, c, cam);
        }
        if (s.face != null && s.reveal > 0.01f) {
            s.texture = s.face;
            s.layerAlpha = s.alpha * s.reveal;
            // still a cold ghostly tint, warming toward its real colours at the end
            s.r = Math.round(Mth.lerp(s.reveal, 225f, 240f));
            s.g = Math.round(Mth.lerp(s.reveal, 235f, 244f));
            s.b = 255;
            super.submit(s, pose, c, cam);
        }
    }

    @Override
    public Identifier getTextureLocation(State s) { return s.texture; }

    @Override
    protected RenderType getRenderType(State s, boolean visible, boolean translucent, boolean glowing) {
        return RenderTypes.entityTranslucent(getTextureLocation(s));
    }

    @Override
    protected int getModelTint(State s) {
        return ARGB.color(Math.round(s.layerAlpha * 255), s.r, s.g, s.b);
    }

    /** stays white in rain and at night instead of taking on the dark sky's colour */
    @Override
    protected int getBlockLightLevel(Wraith e, net.minecraft.core.BlockPos p) { return 15; }

    @Override
    protected boolean shouldShowName(Wraith e, double d) { return false; }
}
