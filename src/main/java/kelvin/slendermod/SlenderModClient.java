package kelvin.slendermod;

import kelvin.slendermod.client.SlendermanMagicParticle;
import kelvin.slendermod.client.block.renderers.RotatableBlockEntityRenderer;
import kelvin.slendermod.client.entity.renderers.*;
import kelvin.slendermod.client.screen.SafeScreen;
import kelvin.slendermod.entity.AdultSCPSlenderEntity;
import kelvin.slendermod.entity.SmallSCPSlenderEntity;
import kelvin.slendermod.item.FlashlightItem;
import kelvin.slendermod.registry.*;
import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;

import java.util.Random;

public class SlenderModClient implements ClientModInitializer {

    private static final ManagedShaderEffect STATIC_SHADER = ShaderEffectManager.getInstance().manage(SlenderMod.id("shaders/post/static.json"));

    public static KeyBinding CRAWL_KEY;
    private static float FRIGHT_BLUR = 0.0f;
    private static float FEAR_ZOOM = 1.0f;
    private static float SCARED_TIMER = 0;
    private static float AMBIANCE_TIMER = 0;
    private static float BREATH_TIMER = 0;
    private static float HEARTBEAT_DELAY = 0;
    private static float I_TIME = 0;
    private static SoundInstance AMBIANCE_INSTANCE;

    @Override
    public void onInitializeClient() {
        SoundRegistry.register();

        EntityRendererRegistry.register(EntityRegistry.SCP_SLENDERMAN, SCPSlendermanRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.SCP_SLENDERWOMAN, SCPSlenderwomanRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.SMALL_SCP_SLENDER, SmallSCPSlenderRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.SLENDER_BOSS, SlenderBossRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.SLENDERMAN, SlendermanRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.SCP_SLENDERMAN_HEAD, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.DEAD_LEAVES, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.SHELF_CONS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.BARBED_WIRE_FENCE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.BONES, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.HOSPITAL_BED, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.RADIO, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.SAFE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TRASH_BIN, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.MISSING_PERSON_POSTER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.DUMPSTER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.NO_ENTRY_SIGN, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.BARBED_WIRE_ROLL, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.AIR_CONDITIONER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.BLOOD, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.CCTV_CAMERA, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.WALKMAN, RenderLayer.getCutout());


        for (Block note : BlockRegistry.PAGES)
            BlockRenderLayerMap.INSTANCE.putBlock(note, RenderLayer.getCutout());

        BlockEntityRendererFactories.register(BlockEntityRegistry.ROTATABLE_BLOCK_ENTITY, ctx -> new RotatableBlockEntityRenderer());
        BlockEntityRendererFactories.register(BlockEntityRegistry.SAFE_BLOCK_ENTITY, SafeBlockRenderer::new);

        ModelPredicateProviderRegistry.register(ItemRegistry.FLASHLIGHT, SlenderMod.id("powered"), (stack, world, entity, seed) -> FlashlightItem.isFlashlightPowered(stack) ? 1 : 0);

        CRAWL_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.slendermod.crawl", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_G, "key.categories.movement"));

        ClientTickEvents.START_CLIENT_TICK.register(SlenderModClient::clientTick);
        ShaderEffectRenderCallback.EVENT.register(SlenderModClient::renderShaderEffects);

        ParticleFactoryRegistry.getInstance().register(ParticleRegistry.SLENDERMAN_MAGIC, SlendermanMagicParticle.Factory::new);

        HandledScreens.register(ScreenHandlerRegistry.TRASH_BIN_SCREEN_HANDLER, HopperScreen::new);
        HandledScreens.register(ScreenHandlerRegistry.SAFE_SCREEN_HANDLER, SafeScreen::new);
    }

    private static void clientTick(MinecraftClient client) {
        if (client.isPaused() || !ConfigRegistry.INSTANCE.getConfig().enableSlenderEffects) {
            return;
        }

        float tickDelta = 1.0f / 20.0f;
        if (SCARED_TIMER > 0) {
            if (HEARTBEAT_DELAY <= 0) {
                Entity camera = client.getCameraEntity();
                if (camera != null) {
                    playPositionlessSound(client, camera.getX(), camera.getY(), camera.getZ(), SoundRegistry.HEARTBEAT, 1.0f);
                    HEARTBEAT_DELAY = 20 * 5;
                }
            }
            FEAR_ZOOM = MathHelper.lerp(tickDelta * 8, FEAR_ZOOM, 0.5f);
            FRIGHT_BLUR = MathHelper.lerp(tickDelta * 4, FRIGHT_BLUR, 1);

            SCARED_TIMER -= tickDelta;
            if (SCARED_TIMER <= 0.01f) {
                SCARED_TIMER = 0;
            }
        } else {
            FRIGHT_BLUR = MathHelper.lerp(tickDelta, FRIGHT_BLUR, 0);
            if (FRIGHT_BLUR <= 0.01f) {
                FEAR_ZOOM = 2.0f;
                FRIGHT_BLUR = 0;
            }
        }

        if (AMBIANCE_TIMER > 0) {
            AMBIANCE_TIMER--;
        }
        if (HEARTBEAT_DELAY > 0) {
            HEARTBEAT_DELAY--;
        }
        if (BREATH_TIMER > 0) {
            BREATH_TIMER--;
        }

        if (SCARED_TIMER <= 0) {
            client.getSoundManager().stop(AMBIANCE_INSTANCE);
        }
    }

    private static void renderShaderEffects(float tickDelta) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        boolean isNear = false;

        if (minecraft == null || !ConfigRegistry.INSTANCE.getConfig().enableSlenderEffects) {
            return;
        }

        if (minecraft.world != null) {
            for (Entity entity : minecraft.world.getEntities()) {
                if (entity instanceof AdultSCPSlenderEntity || entity instanceof SmallSCPSlenderEntity) {
                    if (minecraft.player != null) {
                        if (minecraft.player.distanceTo(entity) <= 10) {
                            isNear = true;
                        }
                    }

                    if (lookingAtEntity(minecraft, entity)) {
                        scare(minecraft);
                    }
                }
            }

            if (isNear) {
                STATIC_SHADER.setUniformValue("iTime", I_TIME);
                STATIC_SHADER.setUniformValue("intensity", ConfigRegistry.INSTANCE.getConfig().staticIntensity);
                STATIC_SHADER.setUniformValue("speed", ConfigRegistry.INSTANCE.getConfig().staticSpeed);

                I_TIME += tickDelta;
                STATIC_SHADER.render(tickDelta);
            }
        }
    }

    private static void scare(MinecraftClient minecraft) {
        float x = 0;
        float y = 0;
        float z = 0;

        if (minecraft.cameraEntity != null) {
            x = (float) minecraft.cameraEntity.getX();
            y = (float) minecraft.cameraEntity.getY();
            z = (float) minecraft.cameraEntity.getZ();
        }

        if (SCARED_TIMER <= 0) {
            playPositionlessSound(minecraft, x, y, z, SoundRegistry.SHOCK, new Random().nextFloat() * 0.5f - 0.25f + 1.0f);

            FEAR_ZOOM = 2.0f;
        }
        if (BREATH_TIMER <= 0) {
            BREATH_TIMER = 20 * 60;
            playPositionlessSound(minecraft, x, y, z, SoundRegistry.WIND, 1.0f);
        }
        if (AMBIANCE_TIMER <= 0) {
            AMBIANCE_TIMER = 20 * 60 * 2.5f;
            AMBIANCE_INSTANCE = playPositionlessSound(minecraft, x, y, z, SoundRegistry.SOMETHING_APPROACHES, new Random().nextFloat() * 0.5f - 0.25f + 1.0f);
        }
        SCARED_TIMER = 10;
        FEAR_ZOOM = MathHelper.lerp(0.5f, FEAR_ZOOM, 2.0f);
    }

    private static boolean lookingAtEntity(MinecraftClient minecraft, Entity entity) {
        Entity camera = minecraft.getCameraEntity();
        if (camera != null) {
            if (camera instanceof PlayerEntity) {
                Quaternionf quat = minecraft.gameRenderer.getCamera().getRotation();
                Vector3f vec = new Vector3f(0, 0, 1);
                vec.rotate(quat);

                Vec3d lookVec = new Vec3d(vec.x(), vec.y(), vec.z()).normalize();

                Vec3d direction = entity.getPos().subtract(camera.getPos()).normalize();

                double dot = lookVec.dotProduct(direction);
                return dot > 0.5f && ((PlayerEntity) camera).canSee(entity);
            }
        }
        return false;
    }

    private static SoundInstance playPositionlessSound(MinecraftClient minecraft, double x, double y, double z, SoundEvent event, float pitch) {
        PositionedSoundInstance positionedSoundInstance = new PositionedSoundInstance(event.getId(), SoundCategory.AMBIENT, 1, pitch, minecraft.world.random, false, 0, SoundInstance.AttenuationType.NONE, x, y, z, false);
        minecraft.getSoundManager().play(positionedSoundInstance);
        return positionedSoundInstance;
    }
}
