package com.euphoriapatches.euphoria_patcher.forge;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GameRuleChecker {

    private static GameRuleChecker instance;
    public static GameRuleChecker getInstance() {
        if (instance == null) instance = new GameRuleChecker();
        return instance;
    }

    private enum Strategy { UNKNOWN, SRG_CLIENT, MOJMAP_CLIENT, MOJMAP_SERVER_OLD, MOJMAP_SERVER_NEW, MOJMAP_MODERN, FAILED }

    private Strategy strategy = Strategy.UNKNOWN;

    // SRG_CLIENT (old GameRules API, SRG names - pre-1.21-era Forge)
    private Object cachedSrgRuleKey = null;
    private Method cachedSrgGetBoolean = null;
    private Method cachedSrgGetGameRules = null;
    private Field  cachedSrgLevelField = null;
    private Method cachedSrgGetInstance = null;
    private Class<?> cachedSrgMinecraftClass = null;

    // Shared (Mojmap strategies)
    private Class<?> cachedMinecraftClass = null;
    private Method   cachedGetInstance = null;

    // MOJMAP_CLIENT (old GameRules API via client level)
    private Field  cachedLevelField = null;
    private Method cachedGetGameRulesFromLevel = null;
    private Object cachedRuleKeyOld = null;
    private Method cachedGetBooleanOld = null;

    // MOJMAP_SERVER_OLD (old GameRules API via integrated server)
    private Method cachedHasSingleplayerServer = null;
    private Method cachedGetSingleplayerServer = null;
    private Method cachedGetGameRulesFromServer = null;

    // MOJMAP_SERVER_NEW (new GameRules API via world data)
    private Method cachedIsLocalServer = null;
    private Method cachedGetWorldData = null;
    private Method cachedGetGameRulesFromWorldData = null;
    private Object cachedRuleKeyNew = null;
    private Method cachedGetNew = null;

    // MOJMAP_MODERN (new GameRules API directly off the server)
    private Method cachedGetGameRulesFromServerNew = null;

    public boolean isTimeAdvancing() {
        if (strategy == Strategy.UNKNOWN) discover();
        try {
            return query();
        } catch (Throwable t) {
            debugLog("Query failed mid-session: " + t.getMessage());
            strategy = Strategy.UNKNOWN;
            resetCache();
            return true;
        }
    }

    private boolean query() throws Throwable {
        if (strategy == Strategy.SRG_CLIENT)        return srgClient();
        if (strategy == Strategy.MOJMAP_CLIENT)     return client();
        if (strategy == Strategy.MOJMAP_SERVER_OLD) return serverOld();
        if (strategy == Strategy.MOJMAP_SERVER_NEW) return serverNew();
        if (strategy == Strategy.MOJMAP_MODERN)     return modern();
        return true;
    }

    private void discover() {
        debugLog("Discovering gamerule strategy...");
        for (Strategy s : new Strategy[]{
                Strategy.SRG_CLIENT,
                Strategy.MOJMAP_CLIENT,
                Strategy.MOJMAP_SERVER_OLD,
                Strategy.MOJMAP_SERVER_NEW,
                Strategy.MOJMAP_MODERN }) {
            strategy = s;
            try {
                query();
                debugLog("Strategy found: " + s);
                return;
            } catch (Throwable t) {
                debugLog(s + " failed: " + t.getMessage());
                resetCache();
            }
        }
        debugLog("All strategies failed");
        strategy = Strategy.FAILED;
    }

    private boolean srgClient() throws Throwable {
        if (cachedSrgMinecraftClass == null) {
            cachedSrgMinecraftClass = Class.forName("net.minecraft.client.Minecraft");
            cachedSrgGetInstance    = ReflectionUtils.accessible(cachedSrgMinecraftClass.getMethod("m_91087_"));
            cachedSrgLevelField     = cachedSrgMinecraftClass.getField("f_91073_");
        }

        Object minecraft = cachedSrgGetInstance.invoke(null);
        if (minecraft == null) return true;

        Object level = cachedSrgLevelField.get(minecraft);
        if (level == null) return true;

        if (cachedSrgGetGameRules == null)
            cachedSrgGetGameRules = ReflectionUtils.accessible(level.getClass().getMethod("m_46469_"));

        Object gameRules = cachedSrgGetGameRules.invoke(level);

        if (cachedSrgRuleKey == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.GameRules");
            cachedSrgRuleKey = ReflectionUtils.getFieldValue(gameRulesClass, "f_46140_");
        }

        if (cachedSrgGetBoolean == null)
            cachedSrgGetBoolean = ReflectionUtils.accessible(gameRules.getClass().getMethod("m_46207_", cachedSrgRuleKey.getClass()));

        return (boolean) cachedSrgGetBoolean.invoke(gameRules, cachedSrgRuleKey);
    }

    private void ensureMinecraftInstance() throws Exception {
        if (cachedMinecraftClass == null) {
            cachedMinecraftClass = Class.forName("net.minecraft.client.Minecraft");
            cachedGetInstance    = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("getInstance"));
        }
    }

    private boolean client() throws Throwable {
        ensureMinecraftInstance();
        if (cachedLevelField == null)
            cachedLevelField = cachedMinecraftClass.getField("level");

        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft == null) return true;

        Object level = cachedLevelField.get(minecraft);
        if (level == null) return true;

        if (cachedGetGameRulesFromLevel == null)
            cachedGetGameRulesFromLevel = ReflectionUtils.accessible(level.getClass().getMethod("getGameRules"));

        Object gameRules = cachedGetGameRulesFromLevel.invoke(level);

        if (cachedRuleKeyOld == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.GameRules");
            cachedRuleKeyOld = ReflectionUtils.getFieldValue(gameRulesClass, "RULE_DAYLIGHT");
        }

        if (cachedGetBooleanOld == null)
            cachedGetBooleanOld = ReflectionUtils.accessible(gameRules.getClass().getMethod("getBoolean", cachedRuleKeyOld.getClass()));

        return (boolean) cachedGetBooleanOld.invoke(gameRules, cachedRuleKeyOld);
    }

    private boolean serverOld() throws Throwable {
        ensureMinecraftInstance();
        if (cachedHasSingleplayerServer == null)
            cachedHasSingleplayerServer = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("hasSingleplayerServer"));
        if (cachedGetSingleplayerServer == null)
            cachedGetSingleplayerServer = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("getSingleplayerServer"));

        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft == null) return true;

        boolean hasServer = (boolean) cachedHasSingleplayerServer.invoke(minecraft);
        if (!hasServer) return true;

        Object server = cachedGetSingleplayerServer.invoke(minecraft);
        if (server == null) return true;

        if (cachedGetGameRulesFromServer == null)
            cachedGetGameRulesFromServer = ReflectionUtils.accessible(server.getClass().getMethod("getGameRules"));

        Object gameRules = cachedGetGameRulesFromServer.invoke(server);

        if (cachedRuleKeyOld == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.GameRules");
            cachedRuleKeyOld = ReflectionUtils.getFieldValue(gameRulesClass, "RULE_DAYLIGHT");
        }

        if (cachedGetBooleanOld == null)
            cachedGetBooleanOld = ReflectionUtils.accessible(gameRules.getClass().getMethod("getBoolean", cachedRuleKeyOld.getClass()));

        return (boolean) cachedGetBooleanOld.invoke(gameRules, cachedRuleKeyOld);
    }

    private boolean serverNew() throws Throwable {
        ensureMinecraftInstance();
        if (cachedIsLocalServer == null)
            cachedIsLocalServer = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("isLocalServer"));
        if (cachedGetSingleplayerServer == null)
            cachedGetSingleplayerServer = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("getSingleplayerServer"));

        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft == null) return true;

        boolean isLocal = (boolean) cachedIsLocalServer.invoke(minecraft);
        if (!isLocal) return true;

        Object server = cachedGetSingleplayerServer.invoke(minecraft);
        if (server == null) return true;

        if (cachedGetWorldData == null)
            cachedGetWorldData = ReflectionUtils.accessible(server.getClass().getMethod("getWorldData"));
        Object worldData = cachedGetWorldData.invoke(server);

        if (cachedGetGameRulesFromWorldData == null)
            cachedGetGameRulesFromWorldData = ReflectionUtils.accessible(worldData.getClass().getMethod("getGameRules"));
        Object gameRules = cachedGetGameRulesFromWorldData.invoke(worldData);

        if (cachedRuleKeyNew == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.gamerules.GameRules");
            cachedRuleKeyNew = ReflectionUtils.getFieldValue(gameRulesClass, "ADVANCE_TIME");
        }

        if (cachedGetNew == null)
            cachedGetNew = ReflectionUtils.accessible(gameRules.getClass().getMethod("get", cachedRuleKeyNew.getClass()));

        return (boolean) cachedGetNew.invoke(gameRules, cachedRuleKeyNew);
    }

    private boolean modern() throws Throwable {
        ensureMinecraftInstance();
        if (cachedGetSingleplayerServer == null)
            cachedGetSingleplayerServer = ReflectionUtils.accessible(cachedMinecraftClass.getMethod("getSingleplayerServer"));

        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft == null) return true;

        Object server = cachedGetSingleplayerServer.invoke(minecraft);
        if (server == null) return true;

        if (cachedGetGameRulesFromServerNew == null)
            cachedGetGameRulesFromServerNew = ReflectionUtils.accessible(server.getClass().getMethod("getGameRules"));
        Object gameRules = cachedGetGameRulesFromServerNew.invoke(server);

        if (cachedRuleKeyNew == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.gamerules.GameRules");
            cachedRuleKeyNew = ReflectionUtils.getFieldValue(gameRulesClass, "ADVANCE_TIME");
        }

        if (cachedGetNew == null)
            cachedGetNew = ReflectionUtils.accessible(gameRules.getClass().getMethod("get", cachedRuleKeyNew.getClass()));

        return (boolean) cachedGetNew.invoke(gameRules, cachedRuleKeyNew);
    }

    private void resetCache() {
        cachedSrgRuleKey = null;
        cachedSrgGetBoolean = null;
        cachedSrgGetGameRules = null;
        cachedSrgLevelField = null;
        cachedSrgGetInstance = null;
        cachedSrgMinecraftClass = null;

        cachedMinecraftClass = null;
        cachedGetInstance = null;

        cachedLevelField = null;
        cachedGetGameRulesFromLevel = null;
        cachedRuleKeyOld = null;
        cachedGetBooleanOld = null;

        cachedHasSingleplayerServer = null;
        cachedGetSingleplayerServer = null;
        cachedGetGameRulesFromServer = null;

        cachedIsLocalServer = null;
        cachedGetWorldData = null;
        cachedGetGameRulesFromWorldData = null;
        cachedRuleKeyNew = null;
        cachedGetNew = null;

        cachedGetGameRulesFromServerNew = null;
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[GameRuleChecker] " + message);
    }
}
