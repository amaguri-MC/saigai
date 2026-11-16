package io.github.kestrelworks.calamities.client;

import io.github.kestrelworks.calamities.Calamities;
import io.github.kestrelworks.calamities.net.QuakeFx;
import io.github.kestrelworks.calamities.net.WindFx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;

public class CalamitiesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Calamities.STORM_BOLT, LightningBoltRenderer::new);
        EntityRendererRegistry.register(Calamities.WRAITH, WraithRenderer::new);
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(Calamities.STREAK, StreakParticle.Provider::new);
        ClientPlayNetworking.registerGlobalReceiver(QuakeFx.TYPE, (p, ctx) -> ClientFx.startQuake(p.magnitude(), p.ticks(), p.start()));
        ClientPlayNetworking.registerGlobalReceiver(WindFx.TYPE, (p, ctx) ->
                ClientFx.startWind(p.dirX(), p.dirZ(), p.push(), p.endFactor(), p.ticks(), p.variant(), p.start()));
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(Calamities.FLAKE, FlakeParticle.Provider::new);
        ClientPlayNetworking.registerGlobalReceiver(io.github.kestrelworks.calamities.net.BlizzardFx.TYPE, (p, ctx) ->
                ClientFx.startBlizzard(p.dirX(), p.dirZ(), p.ticks(), p.start()));
        ClientTickEvents.END_CLIENT_TICK.register(ClientFx::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> ClientFx.stopAll());
    }
}
