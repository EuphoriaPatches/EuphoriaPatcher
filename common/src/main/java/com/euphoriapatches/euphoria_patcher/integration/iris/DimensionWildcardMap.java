package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Custom {@code Map} replacing Iris's {@code dimensionMap} in {@code ShaderPack},
 * adding wildcard ('*') support to {@code dimension.properties} patterns (e.g., "namespace:name*").
 * <p>
 * Matches in reverse insertion order so specific rules take priority over catch-alls, EXCEPT for the Iris {@code ":*"} catch-all. Per Iris definition, it acts as the lowest-priority fallback for unassigned dimensions, regardless of its declaration order.
 * <p>
 * Uses reflection to read target key properties without direct Iris compile dependencies.
 */
public class DimensionWildcardMap implements Map<Object, Object> {
    private static final ThreadLocal<DimensionWildcardMap> BUILDER = new ThreadLocal<>();

    /** Sentinel stored in {@link #matchCache} to remember a resolved "no match" without hitting {@code null}. */
    private static final PatternEntry NO_MATCH = new PatternEntry(null, null, null, null, null);

    private final List<PatternEntry> entries = new ArrayList<>();
    private final Map<String, PatternEntry> matchCache = new java.util.HashMap<>();

    public static void beginBuilding() {
        BUILDER.set(new DimensionWildcardMap());
    }

    public static DimensionWildcardMap currentBuilder() {
        return BUILDER.get();
    }

    public static void finishBuilding() {
        BUILDER.remove();
    }

    private static final class PatternEntry {
        final String rawNamespace;
        final String rawName;
        final Pattern namespacePattern;
        final Pattern namePattern;
        final Object value;
        /** True for the bare Iris {@code *} catch-all ("*:*") - always lowest match priority. */
        final boolean isUniversalCatchAll;

        PatternEntry(String rawNamespace, String rawName, Pattern namespacePattern, Pattern namePattern, Object value) {
            this.rawNamespace = rawNamespace;
            this.rawName = rawName;
            this.namespacePattern = namespacePattern;
            this.namePattern = namePattern;
            this.value = value;
            this.isUniversalCatchAll = "*".equals(rawNamespace) && "*".equals(rawName);
        }
    }

    @Override
    public Object put(Object key, Object value) {
        String[] parts = extractNamespaceAndName(key);
        if (parts == null) {
            debugLog("Ignoring dimensionMap entry, key had no readable getNamespace()/getName(): " + key);
            return null;
        }

        registerEntry(parts[0], parts[1], value);
        return null;
    }

    public void registerEntry(String rawPart, Object value) {
        int colonIdx = rawPart.indexOf(':');
        String namespace = colonIdx == -1 ? "minecraft" : rawPart.substring(0, colonIdx);
        String name = colonIdx == -1 ? rawPart : rawPart.substring(colonIdx + 1);
        registerEntry(namespace, name, value);
    }

    public void registerEntry(String namespace, String name, Object value) {
        entries.add(new PatternEntry(namespace, name, toPattern(namespace), toPattern(name), value));
        matchCache.clear();
        debugLog("Registered dimension entry " + namespace + ":" + name + " -> " + value);
    }

    @Override
    public Object get(Object key) {
        PatternEntry match = findMatch(key);
        return match == null ? null : match.value;
    }

    @Override
    public boolean containsKey(Object key) {
        return findMatch(key) != null;
    }

    private PatternEntry findMatch(Object key) {
        String[] parts = extractNamespaceAndName(key);
        if (parts == null) {
            return null;
        }

        String cacheKey = parts[0] + ':' + parts[1];
        PatternEntry cached = matchCache.get(cacheKey);
        if (cached != null) {
            return cached == NO_MATCH ? null : cached;
        }

        // First pass: everything except the iris "*" catch-all
        for (int i = entries.size() - 1; i >= 0; i--) {
            PatternEntry entry = entries.get(i);
            if (entry.isUniversalCatchAll) {
                continue;
            }
            if (entry.namespacePattern.matcher(parts[0]).matches() && entry.namePattern.matcher(parts[1]).matches()) {
                debugLog("Matched " + parts[0] + ":" + parts[1] + " against " + entry.rawNamespace + ":" + entry.rawName + " -> " + entry.value);
                matchCache.put(cacheKey, entry);
                return entry;
            }
        }

        // Second pass: fall back to the bare "*" catch-all
        for (int i = entries.size() - 1; i >= 0; i--) {
            PatternEntry entry = entries.get(i);
            if (entry.isUniversalCatchAll) {
                debugLog("Matched " + parts[0] + ":" + parts[1] + " against catch-all * -> " + entry.value);
                matchCache.put(cacheKey, entry);
                return entry;
            }
        }

        debugLog("No dimension entry matched " + parts[0] + ":" + parts[1] + " (checked " + entries.size() + " entries)");
        matchCache.put(cacheKey, NO_MATCH);
        return null;
    }

    private static String[] extractNamespaceAndName(Object key) {
        if (key == null) {
            return null;
        }

        try {
            Method getNamespace = key.getClass().getMethod("getNamespace");
            Method getName = key.getClass().getMethod("getName");
            String namespace = (String) getNamespace.invoke(key);
            String name = (String) getName.invoke(key);
            if (namespace == null || name == null) {
                return null;
            }
            return new String[]{namespace, name};
        } catch (ReflectiveOperationException | ClassCastException e) {
            debugLog("Failed to read getNamespace()/getName() off " + key.getClass().getName() + ": " + e);
            return null;
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[DimensionWildcardMap] " + message);
    }

    private static Pattern toPattern(String raw) {
        if (raw.indexOf('*') < 0) {
            return Pattern.compile(Pattern.quote(raw));
        }

        String[] segments = raw.split("\\*", -1);
        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(segments[i]));
        }

        return Pattern.compile(regex.toString());
    }

    // Everything below is unused by Iris for this field but is implemented defensively - returning
    // safe/empty results rather than throwing - in case some other code path ever touches it.

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        for (PatternEntry entry : entries) {
            if (Objects.equals(entry.value, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map<?, ?> m) {
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        entries.clear();
        matchCache.clear();
    }

    @Override
    public Set<Object> keySet() {
        return Collections.emptySet();
    }

    @Override
    public Collection<Object> values() {
        List<Object> values = new ArrayList<>(entries.size());
        for (PatternEntry entry : entries) {
            values.add(entry.value);
        }
        return values;
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return Collections.emptySet();
    }
}
