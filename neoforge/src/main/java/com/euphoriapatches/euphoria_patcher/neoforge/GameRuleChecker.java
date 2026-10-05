package com.euphoriapatches.euphoria_patcher.neoforge;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameRules;


import java.lang.reflect.Method;

public class GameRuleChecker {

    private static GameRuleChecker instance;
    public static GameRuleChecker getInstance() {
        if (instance == null) instance = new GameRuleChecker();
        return instance;
    }

    private enum Strategy { UNKNOWN, CLIENT, SERVER_OLD, SERVER_NEW, MODERN, FAILED }

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
        if (strategy == Strategy.CLIENT)     return Client();
        if (strategy == Strategy.SERVER_OLD) return ServerOld();
        if (strategy == Strategy.SERVER_NEW) return ServerNew();
        if (strategy == Strategy.MODERN)     return modern();
        return true;
    }

    private void discover() {
        debugLog("Discovering gamerule strategy...");
        for (Strategy s : new Strategy[]{
                Strategy.CLIENT,
                Strategy.SERVER_OLD,
                Strategy.SERVER_NEW,
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

    private boolean Client() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return true;
        return client.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    private boolean ServerOld() {
        Minecraft client = Minecraft.getInstance();
            if (!client.hasSingleplayerServer() || client.getSingleplayerServer() == null) return true;
        return client.getSingleplayerServer().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    private boolean ServerNew() throws Exception {
        Minecraft client = Minecraft.getInstance();
        if (!client.isLocalServer() || client.getSingleplayerServer() == null) return true;

        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) return true;

        Object worldData = server.getWorldData();
        Object gameRules = worldData.getClass().getMethod("getGameRules").invoke(worldData);

        if (cachedRuleKey == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.gamerules.GameRules");
            cachedRuleKey = ReflectionUtils.getFieldValue(gameRulesClass, "ADVANCE_TIME");
        }
        if (cachedMethod == null)
            cachedMethod = ReflectionUtils.accessible(gameRules.getClass().getMethod("get", cachedRuleKey.getClass()));

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