package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class EuphoriaMixinPlugin implements IMixinConfigPlugin {
    public static final String LEGACY_IRIS_CLASS = "net.coderbot.iris.gl.shader.StandardMacros";
    public static final String MODERN_IRIS_CLASS = "net.irisshaders.iris.gl.shader.StandardMacros";
    public static final String MINECRAFT_CLIENT_CLASS = "net.minecraft.client.Minecraft";
    public static final String MINECRAFT_CLIENT_YARN_CLASS = "net.minecraft.class_310";
    public static final String IRIS_HEADER_ENTRY_CLASS = "net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry";
    public static final String IRIS_SHADER_PROPERTIES_CLASS = "net.irisshaders.iris.shaderpack.properties.ShaderProperties";
    public static final String IRIS_OPTION_MENU_ELEMENT_CLASS = "net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement";
    public static final String IRIS_OPTION_MENU_CONTAINER_CLASS = "net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer";
    public static final String IRIS_PROFILE_ELEMENT_WIDGET_CLASS = "net.irisshaders.iris.gui.element.widget.ProfileElementWidget";
    public static final String IRIS_PACK_RENDER_TARGETS_CLASS = "net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives";
    public static final String LEGACY_IRIS_SHADER_PROPERTIES_CLASS = "net.coderbot.iris.shaderpack.ShaderProperties";
    public static final String LEGACY_IRIS_OPTION_MENU_ELEMENT_CLASS = "net.coderbot.iris.shaderpack.option.menu.OptionMenuElement";
    public static final String LEGACY_IRIS_OPTION_MENU_CONTAINER_CLASS = "net.coderbot.iris.shaderpack.option.menu.OptionMenuContainer";
    public static final String LEGACY_IRIS_PROFILE_ELEMENT_WIDGET_CLASS = "net.coderbot.iris.gui.element.widget.ProfileElementWidget";
    public static final String LEGACY_IRIS_PACK_RENDER_TARGETS_CLASS = "net.coderbot.iris.shaderpack.PackRenderTargetDirectives";
    public static final String PHOTONICS_RAYTRACER_CLASS = "at.redi2go.photonic.client.Raytracer";
    public static final String RENDER_TYPE_CLASS = "net.minecraft.client.renderer.rendertype.RenderType";
    public static final String RENDER_TYPE_CLASS_YARN = "net.minecraft.class_1921";
    public static final String ENDER_DRAGON_RENDERER_CLASS = "net.minecraft.class_895";
    public static final String RENDER_STATE_SHARD_CLASS = "net.minecraft.class_4668";
    public static final String IRIS_EXCLUSIVE_UNIFORMS_CLASS = "net.irisshaders.iris.uniforms.IrisExclusiveUniforms";
    public static final String IRIS_EXCLUSIVE_UNIFORMS_CLASS_LEGACY = "net.coderbot.iris.uniforms.IrisExclusiveUniforms";
    public static final String PREPARED_RENDER_TYPE_CLASS = "net.minecraft.client.renderer.rendertype.PreparedRenderType";
    public static final String IRIS_ELEMENT_ROW_CLASS = "net.irisshaders.iris.gui.element.IrisElementRow";
    public static final String LEGACY_IRIS_ELEMENT_ROW_CLASS = "net.coderbot.iris.gui.element.IrisElementRow";
    public static final String MODERN_IRIS_EXTENDED_DATA_HELPER_CLASS = "net.irisshaders.iris.vertices.ExtendedDataHelper";
    public static final String LEGACY_IRIS_EXTENDED_DATA_HELPER_CLASS = "net.coderbot.iris.vertices.ExtendedDataHelper";
    public static final String MODERN_SHADER_PACK_CLASS = "net.irisshaders.iris.shaderpack.ShaderPack";
    public static final String LEGACY_SHADER_PACK_CLASS = "net.coderbot.iris.shaderpack.ShaderPack";
    public static final String MODERN_PIPELINE_MANAGER_CLASS = "net.irisshaders.iris.pipeline.PipelineManager";
    public static final String LEGACY_PIPELINE_MANAGER_CLASS = "net.coderbot.iris.pipeline.PipelineManager";
    public static final String NATIVE_IMAGE_CLASS = "com.mojang.blaze3d.platform.NativeImage";
    public static final String NATIVE_IMAGE_CLASS_YARN = "net.minecraft.class_1011";
    public static final String SCREENSHOT_CLASS = "net.minecraft.client.Screenshot";
    public static final String SCREENSHOT_CLASS_YARN = "net.minecraft.class_318";
    public static final String IRIS_SHADER_PACK_SCREEN_CLASS = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    public static final String LEGACY_IRIS_SHADER_PACK_SCREEN_CLASS = "net.coderbot.iris.gui.screen.ShaderPackScreen";
    public static final String MODERN_IRIS_MAIN_CLASS = "net.irisshaders.iris.Iris";
    public static final String LEGACY_IRIS_MAIN_CLASS = "net.coderbot.iris.Iris";

    @Override
    public void onLoad(String mixinPackage) {
        // No initialization needed
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("IrisLegacyStandardMacrosMixin")) {
            return checkClassExists(LEGACY_IRIS_CLASS);
        }

        if (mixinClassName.contains("IrisModernStandardMacrosMixin")) {
            return checkClassExists(MODERN_IRIS_CLASS);
        }

        // New Minecraft class (net.minecraft.client.Minecraft) - for newer versions
        if (mixinClassName.contains("ClientTickMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(MINECRAFT_CLIENT_CLASS);
        }

        if (mixinClassName.contains("ReloadShadersOnDimensionChangeMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(MINECRAFT_CLIENT_CLASS);
        }

        // Old Yarn mappings (net.minecraft.client.MinecraftClient) - for older versions
        if (mixinClassName.contains("ClientTickMixinYarn")) {
            return checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }

        if (mixinClassName.contains("ReloadShadersOnDimensionChangeMixinYarn")) {
            return checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }

        if (mixinClassName.contains("IrisHeaderEntryMixinYarn")) {
            return checkClassExists(IRIS_HEADER_ENTRY_CLASS) && checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }

        if (mixinClassName.contains("IrisHeaderEntryMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(IRIS_HEADER_ENTRY_CLASS) && checkClassExists(MINECRAFT_CLIENT_CLASS);
        }

        if (mixinClassName.contains("IrisModernShaderPropertiesMixin")) {
            return checkClassExists(IRIS_SHADER_PROPERTIES_CLASS);
        }

        if (mixinClassName.contains("IrisModernOptionMenuElementMixin")) {
            return checkClassExists(IRIS_OPTION_MENU_ELEMENT_CLASS);
        }

        if (mixinClassName.contains("IrisModernOptionMenuContainerMixin")) {
            return checkClassExists(IRIS_OPTION_MENU_CONTAINER_CLASS);
        }

        if (mixinClassName.contains("IrisModernProfileElementWidgetMixin")) {
            return checkClassExists(IRIS_PROFILE_ELEMENT_WIDGET_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyShaderPropertiesMixin")) {
            return checkClassExists(LEGACY_IRIS_SHADER_PROPERTIES_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyOptionMenuElementMixin")) {
            return checkClassExists(LEGACY_IRIS_OPTION_MENU_ELEMENT_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyOptionMenuContainerMixin")) {
            return checkClassExists(LEGACY_IRIS_OPTION_MENU_CONTAINER_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyProfileElementWidgetMixin")) {
            return checkClassExists(LEGACY_IRIS_PROFILE_ELEMENT_WIDGET_CLASS);
        }

        if (mixinClassName.contains("IrisModernPackRenderTargetDirectivesMixin")) {
            return checkClassExists(IRIS_PACK_RENDER_TARGETS_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyPackRenderTargetDirectivesMixin")) {
            return checkClassExists(LEGACY_IRIS_PACK_RENDER_TARGETS_CLASS);
        }

        if (mixinClassName.contains("PhotonicsRaytracerMixin")) {
            return checkClassExists(PHOTONICS_RAYTRACER_CLASS);
        }

        if  (mixinClassName.contains("EnderDragonRendererMixin")) {
            return checkClassExists(ENDER_DRAGON_RENDERER_CLASS) && checkClassExists(MINECRAFT_CLIENT_YARN_CLASS) && checkClassExists(RENDER_STATE_SHARD_CLASS);
        }

        if (mixinClassName.contains("RenderTypeMixinYarn")) {
            return checkClassExists(RENDER_TYPE_CLASS_YARN) && checkClassExists(MINECRAFT_CLIENT_YARN_CLASS) && !checkClassExists(RENDER_STATE_SHARD_CLASS);
        }

        if (mixinClassName.contains("IrisModernExclusiveUniformsMixin")) {
            return checkClassExists(IRIS_EXCLUSIVE_UNIFORMS_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyExclusiveUniformsMixin")) {
            return checkClassExists(IRIS_EXCLUSIVE_UNIFORMS_CLASS_LEGACY);
        }

        if (mixinClassName.contains("PreparedRenderTypeMixin")) {
            return checkClassExists(PREPARED_RENDER_TYPE_CLASS);
        }

        if (mixinClassName.contains("IrisElementRowMixin")) {
            return (checkClassExists(IRIS_ELEMENT_ROW_CLASS));
        }

        if (mixinClassName.contains("IrisLegacyElementRowMixin")) {
            return (checkClassExists(LEGACY_IRIS_ELEMENT_ROW_CLASS));
        }

        if (mixinClassName.contains("IrisModernExtendedDataHelperMixin")) {
            return checkClassExists(MODERN_IRIS_EXTENDED_DATA_HELPER_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyExtendedDataHelperMixin")) {
            return checkClassExists(LEGACY_IRIS_EXTENDED_DATA_HELPER_CLASS);
        }

        if (mixinClassName.contains("IrisModernShaderPackMixin")) {
            return checkClassExists(MODERN_SHADER_PACK_CLASS);
        }

        if (mixinClassName.contains("IrisModernPipelineManagerMixin")) {
            return checkClassExists(MODERN_PIPELINE_MANAGER_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyPipelineManagerMixin")) {
            return checkClassExists(LEGACY_PIPELINE_MANAGER_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyShaderPackMixin")) {
            return checkClassExists(LEGACY_SHADER_PACK_CLASS);
        }

        if (mixinClassName.contains("NativeImageMixinYarn")) {
            return checkClassExists(NATIVE_IMAGE_CLASS_YARN);
        }

        if (mixinClassName.contains("NativeImageMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(NATIVE_IMAGE_CLASS);
        }

        if (mixinClassName.contains("ScreenshotMixinYarn")) {
            return checkClassExists(SCREENSHOT_CLASS_YARN);
        }

        if (mixinClassName.contains("ScreenshotMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(SCREENSHOT_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyShaderPackScreenMixin")) {
            return checkClassExists(LEGACY_IRIS_SHADER_PACK_SCREEN_CLASS);
        }

        if (mixinClassName.contains("IrisShaderPackScreenMixin") && !mixinClassName.contains("Legacy")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS);
        }

        if (mixinClassName.contains("SodiumMessagePopupContainerMixinYarn")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS) && checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }

        if (mixinClassName.contains("SodiumMessagePopupContainerMixin")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS) && checkClassExists(MINECRAFT_CLIENT_CLASS);
        }

        if (mixinClassName.contains("SodiumMessagePopup")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS);
        }

        if (mixinClassName.contains("IrisConfigPropertiesMixin") && !mixinClassName.contains("Legacy")) {
            return checkClassExists(MODERN_IRIS_MAIN_CLASS);
        }

        if (mixinClassName.contains("IrisLegacyConfigPropertiesMixin")) {
            return checkClassExists(LEGACY_IRIS_MAIN_CLASS);
        }

        // Apply other mixins by default
        return true;
    }

    private boolean checkClassExists(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return getClass().getClassLoader().getResource(resourceName) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not needed
    }

    @Override
    public List<String> getMixins() {
        return null; // Return mixins defined in the JSON
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Not needed
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Not needed
    }
}
