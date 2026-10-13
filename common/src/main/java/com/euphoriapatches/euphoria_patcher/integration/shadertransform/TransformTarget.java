package com.euphoriapatches.euphoria_patcher.integration.shadertransform;

/**
 * Vanilla render paths a modded shader/pipeline can be transformed into. Iris already does this on vanilla so this is basically the same
 */
public enum TransformTarget {
    ENTITY_SOLID(VanillaNames.NEW_ENTITY_FORMAT, VanillaNames.pipeline("ENTITY_SOLID", "field_56901"),
            "getRendertypeEntitySolidShader", "m_172661_", "method_34502"),

    ENTITY_CUTOUT(VanillaNames.NEW_ENTITY_FORMAT, VanillaNames.pipeline("ENTITY_CUTOUT", "field_56903"),
            "getRendertypeEntityCutoutShader", "m_172664_", "method_34503"),

    ENTITY_TRANSLUCENT(VanillaNames.NEW_ENTITY_FORMAT, VanillaNames.pipeline("ENTITY_TRANSLUCENT", "field_56906"),
            "getRendertypeEntityTranslucentShader", "m_172679_", "method_34508"),

    ENTITY_EMISSIVE(VanillaNames.NEW_ENTITY_FORMAT, VanillaNames.pipeline("ENTITY_TRANSLUCENT_EMISSIVE", "field_56907"),
            "getRendertypeEntityTranslucentEmissiveShader", "m_234223_", "method_42595"),

    EYES(VanillaNames.NEW_ENTITY_FORMAT, VanillaNames.pipeline("EYES", "field_56912"),
            "getRendertypeEyesShader", "m_172700_", "method_34515"),

    LIGHTNING(VanillaNames.POSITION_COLOR_FORMAT, VanillaNames.pipeline("LIGHTNING", "field_56825"),
            "getRendertypeLightningShader", "m_172753_", "method_34531"),

    SKY_TEXTURED(VanillaNames.NONE, VanillaNames.pipeline("CELESTIAL", "field_56878")),

    SKY_COLOR(VanillaNames.NONE, VanillaNames.pipeline("SUNRISE_SUNSET", "field_56876"));

    private final String[] expectedFormatFields;
    private final String[] modernPipelineFields;
    private final String[] legacyGetterNames;

    TransformTarget(String[] expectedFormatFields, String[] modernPipelineFields, String... legacyGetterNames) {
        this.expectedFormatFields = expectedFormatFields;
        this.modernPipelineFields = modernPipelineFields;
        this.legacyGetterNames = legacyGetterNames;
    }

    /** Candidate {@code DefaultVertexFormat} field names of the vertex format a shader must use to be transformed here (legacy only). */
    public String[] expectedFormatFields() {
        return expectedFormatFields;
    }

    /** Candidate names (Mojmap, intermediary) of the vanilla {@code RenderPipelines} field this target copies on 1.21.5+. */
    public String[] modernPipelineFields() {
        return modernPipelineFields;
    }

    /** Candidate names of the {@code GameRenderer} getter that Iris overrides on 1.18.2 to 1.21.1 (Mojmap, SRG, intermediary). */
    public String[] legacyGetterNames() {
        return legacyGetterNames;
    }

    private static final class VanillaNames {
        // For the targets that only exist on 1.21.5+, there is no legacy format or getter to name
        static final String[] NONE = {};
        // DefaultVertexFormat.NEW_ENTITY
        static final String[] NEW_ENTITY_FORMAT = {"NEW_ENTITY", "f_85812_", "field_1580"};
        // DefaultVertexFormat.POSITION_COLOR
        static final String[] POSITION_COLOR_FORMAT = {"POSITION_COLOR", "f_85815_", "field_1576"};

        static String[] pipeline(String mojmapName, String intermediaryName) {
            return new String[]{mojmapName, intermediaryName};
        }
    }
}
