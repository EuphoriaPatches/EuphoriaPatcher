package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class EuphoriaMixinPlugin implements IMixinConfigPlugin {
    public static final String MODERN_IRIS_CLASS = "net.irisshaders.iris.gl.shader.StandardMacros";
    public static final String IRIS_HEADER_ENTRY_CLASS = "net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry";
    public static final String IRIS_SHADER_PROPERTIES_CLASS = "net.irisshaders.iris.shaderpack.properties.ShaderProperties";
    public static final String IRIS_OPTION_MENU_ELEMENT_CLASS = "net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement";
    public static final String IRIS_OPTION_MENU_CONTAINER_CLASS = "net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer";
    public static final String IRIS_PROFILE_ELEMENT_WIDGET_CLASS = "net.irisshaders.iris.gui.element.widget.ProfileElementWidget";
    public static final String IRIS_PACK_RENDER_TARGETS_CLASS = "net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives";
    public static final String RENDER_TYPE_CLASS = "net.minecraft.client.renderer.rendertype.RenderType";
    public static final String ENDER_DRAGON_RENDERER_CLASS = "net.minecraft.client.renderer.entity.EnderDragonRenderer";
    public static final String RENDER_STATE_SHARD_CLASS = "net.minecraft.client.renderer.RenderStateShard";
    public static final String IRIS_EXCLUSIVE_UNIFORMS_CLASS = "net.irisshaders.iris.uniforms.IrisExclusiveUniforms";
    public static final String PREPARED_RENDER_TYPE_CLASS = "net.minecraft.client.renderer.rendertype.PreparedRenderType";
    public static final String IRIS_EXTENDED_DATA_HELPER_CLASS = "net.irisshaders.iris.vertices.ExtendedDataHelper";
    public static final String MODERN_SHADER_PACK_CLASS = "net.irisshaders.iris.shaderpack.ShaderPack";
    public static final String MODERN_PIPELINE_MANAGER_CLASS = "net.irisshaders.iris.pipeline.PipelineManager";
    public static final String NATIVE_IMAGE_CLASS = "com.mojang.blaze3d.platform.NativeImage";
    public static final String SCREENSHOT_CLASS = "net.minecraft.client.Screenshot";
    public static final String IRIS_SHADER_PACK_SCREEN_CLASS = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    public static final String MODERN_IRIS_MAIN_CLASS = "net.irisshaders.iris.Iris";

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
        if (mixinClassName.contains("IrisModernStandardMacrosMixin")) {
            return checkClassExists(MODERN_IRIS_CLASS);
        }

        if (mixinClassName.contains("IrisHeaderEntryMixin")) {
            return checkClassExists(IRIS_HEADER_ENTRY_CLASS);
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
        if (mixinClassName.contains("IrisModernPackRenderTargetDirectivesMixin")) {
            return checkClassExists(IRIS_PACK_RENDER_TARGETS_CLASS);
        }

        if  (mixinClassName.contains("EnderDragonRendererMixin")) {
            return checkClassExists(ENDER_DRAGON_RENDERER_CLASS) && checkClassExists(RENDER_STATE_SHARD_CLASS);
        }

        if (mixinClassName.contains("RenderTypeMixin")) {
            return checkClassExists(RENDER_TYPE_CLASS) && !checkClassExists(RENDER_STATE_SHARD_CLASS);
        }

        if (mixinClassName.contains("IrisModernExclusiveUniformsMixin")) {
            return checkClassExists(IRIS_EXCLUSIVE_UNIFORMS_CLASS);
        }

        if (mixinClassName.contains("PreparedRenderTypeMixin")) {
            return checkClassExists(PREPARED_RENDER_TYPE_CLASS);
        }

        if (mixinClassName.contains("IrisModernExtendedDataHelperMixin")) {
            return checkClassExists(IRIS_EXTENDED_DATA_HELPER_CLASS);
        }

        if (mixinClassName.contains("IrisModernShaderPackMixin")) {
            return checkClassExists(MODERN_SHADER_PACK_CLASS);
        }

        if (mixinClassName.contains("IrisModernPipelineManagerMixin")) {
            return checkClassExists(MODERN_PIPELINE_MANAGER_CLASS);
        }

        if (mixinClassName.contains("NativeImageMixin")) {
            return checkClassExists(NATIVE_IMAGE_CLASS);
        }

        if (mixinClassName.contains("ScreenshotMixin")) {
            return checkClassExists(SCREENSHOT_CLASS);
        }

        if (mixinClassName.contains("IrisShaderPackScreenMixin")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS);
        }

        if (mixinClassName.contains("SodiumMessagePopup")) {
            return checkClassExists(IRIS_SHADER_PACK_SCREEN_CLASS);
        }

        if (mixinClassName.contains("IrisConfigPropertiesMixin")) {
            return checkClassExists(MODERN_IRIS_MAIN_CLASS);
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
