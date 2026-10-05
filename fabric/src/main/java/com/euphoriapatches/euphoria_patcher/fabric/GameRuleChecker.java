package com.euphoriapatches.euphoria_patcher.fabric;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameRules;

import java.lang.reflect.Method;

public class GameRuleChecker {

    private static GameRuleChecker instance;
    public static GameRuleChecker getInstance() {
        if (instance == null) instance = new GameRuleChecker();
        return instance;
    }

    private enum Strategy { UNKNOWN, YARN_CLIENT, YARN_SERVER_OLD, YARN_SERVER_NEW, MODERN, FAILED }

    private Strategy strategy = Strategy.UNKNOWN;
    private Object cachedRuleKey = null;
    private Method cachedMethod = null;

    private Class<?> modernMcClass = null;
    private Method   modernGetInstance = null;
    private Method   modernGetSingleplayerServer = null;
    private Method   modernGetGameRules = null;

    public boolean isTimeAdvancing() {
        if (strategy == Strategy.UNKNOWN) discover();
        try {
            return query();
        } catch (Throwable t) {
            debugLog("Query failed mid-session: " + t.getMessage());
            strategy      = Strategy.UNKNOWN;
            cachedRuleKey = null;
            cachedMethod  = null;
            return true;
        }
    }

    private boolean query() throws Throwable {
        if (strategy == Strategy.YARN_CLIENT)     return yarnClient();
        if (strategy == Strategy.YARN_SERVER_OLD) return yarnServerOld();
        if (strategy == Strategy.YARN_SERVER_NEW) return yarnServerNew();
        if (strategy == Strategy.MODERN)          return modern();
        return true;
    }

    private void discover() {
        debugLog("Discovering gamerule strategy...");
        for (Strategy s : new Strategy[]{
                Strategy.YARN_CLIENT,
                Strategy.YARN_SERVER_OLD,
                Strategy.YARN_SERVER_NEW,
                Strategy.MODERN }) {
            strategy = s;
            try {
                query(); // throws on failure, no catch here
                debugLog("Strategy found: " + s);
                return;
            } catch (Throwable t) {
                debugLog(s + " failed: " + t.getMessage());
                cachedRuleKey = null;
                cachedMethod  = null;
            }
        }
        debugLog("All strategies failed");
        strategy = Strategy.FAILED;
    }

    private boolean yarnClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return true;
        return client.world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
    }

    private boolean yarnServerOld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isIntegratedServerRunning() || client.getServer() == null) return true;
        return client.getServer().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).get();
    }

    private boolean yarnServerNew() throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isIntegratedServerRunning() || client.getServer() == null) return true;
        GameRules gameRules = client.getServer().getSaveProperties().getGameRules();
        if (cachedRuleKey == null)
            cachedRuleKey = ReflectionUtils.getFieldValue(GameRules.class, "field_19396");
        if (cachedMethod == null)
            cachedMethod = gameRules.getClass().getMethod("method_76185", cachedRuleKey.getClass());
        return (boolean) cachedMethod.invoke(gameRules, cachedRuleKey);
    }

    private boolean modern() throws Exception {
        if (modernMcClass == null) {
            modernMcClass               = Class.forName("net.minecraft.client.Minecraft");
            modernGetInstance           = ReflectionUtils.accessible(modernMcClass.getMethod("getInstance"));
            modernGetSingleplayerServer = ReflectionUtils.accessible(modernMcClass.getMethod("getSingleplayerServer"));
            Class<?> gameRulesClass     = Class.forName("net.minecraft.world.level.gamerules.GameRules");
            cachedRuleKey               = ReflectionUtils.getFieldValue(gameRulesClass, "ADVANCE_TIME");
        }

        Object mcInstance = modernGetInstance.invoke(null);
        if (mcInstance == null) return true;
        Object server = modernGetSingleplayerServer.invoke(mcInstance);
        if (server == null) return true;
        Object gameRules = modernGetGameRules == null
                ? null : modernGetGameRules.invoke(server);

        if (modernGetGameRules == null) {
            modernGetGameRules = ReflectionUtils.accessible(server.getClass().getMethod("getGameRules"));
            gameRules          = modernGetGameRules.invoke(server);
        }
        if (cachedMethod == null)
            cachedMethod = ReflectionUtils.accessible(gameRules.getClass().getMethod("get", cachedRuleKey.getClass()));

        return (boolean) cachedMethod.invoke(gameRules, cachedRuleKey);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[GameRuleChecker] " + message);
    }
}