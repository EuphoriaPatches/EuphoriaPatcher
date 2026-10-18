package com.euphoriapatches.euphoria_patcher.integration.shadertransform;

import java.util.HashMap;
import java.util.Map;

/**
 * Modded shaders which are invisible with shaderapcks fixer list
 * <p>
 * Only list a shader whose vertex format matches the target
 */
public final class ShaderTransformTable {
    private static final Map<String, TransformTarget> ENTRIES = new HashMap<String, TransformTarget>();

    static {
        // Forge / NeoForge
        add(TransformTarget.ENTITY_TRANSLUCENT, "forge:rendertype_entity_unlit_translucent", "neoforge:rendertype_entity_unlit_translucent", "neoforge:pipeline/entity_unlit_translucent");

        // Alex's Caves
        add(TransformTarget.ENTITY_TRANSLUCENT, "alexscaves:rendertype_bubbled", "alexscaves:rendertype_ferrouslime_gel",
        "alexscaves:pipeline/ferrouslime_gel", "alexscaves:pipeline/entity_unlit_translucent");

        add(TransformTarget.ENTITY_EMISSIVE, "alexscaves:rendertype_red_ghost", "alexscaves:pipeline/red_ghost");

        // AE2
        add(TransformTarget.LIGHTNING,"ae2:pipeline/storage_cell_leds");

        // Undergarden
        add(TransformTarget.ENTITY_TRANSLUCENT, "undergarden:pipeline/entity_decal_translucent");

        // Botania
        add(TransformTarget.ENTITY_TRANSLUCENT, "botania:doppleganger", "botania:pylon", "botania__doppleganger", "botania__pylon");

    }

    private ShaderTransformTable() {}

    private static void add(TransformTarget target, String... ids) {
        for (String id : ids) {
            ENTRIES.put(id, target);
        }
    }

    public static TransformTarget get(String id) {
        return ENTRIES.get(id);
    }
}
