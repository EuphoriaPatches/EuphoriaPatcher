package com.euphoriapatches.euphoria_patcher.util.mod;

import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.util.EnumMap;

@SuppressWarnings("SpellCheckingInspection")
public class ModChecker {
	private static final String IRIS = ShaderLoader.IRIS;
	private static final String OCULUS = ShaderLoader.OCULUS;
	private static final String OPTIFINE = ShaderLoader.OPTIFINE;
	private static final String ANGELICA = ShaderLoader.ANGELICA;
	private static final String NEOCULUS = ShaderLoader.NEOCULUS;

	private static Class<?> cachedIrisClass = null;
	private static boolean irisClassSearched = false;
	private static String cachedIrisLikeLoader = null;

	private static final String MODERN_IRIS_CLASS = "net.irisshaders.iris.Iris";
	private static final String LEGACY_IRIS_CLASS = "net.coderbot.iris.Iris";

	@SuppressWarnings("SpellCheckingInspection")
	public enum ModNames {
		IRIS(Registers.irisLike(ModChecker.IRIS)),
		OCULUS(Registers.irisLike(ModChecker.OCULUS)),
		ANGELICA(Registers.irisLike(ModChecker.ANGELICA)),
		NEOCULUS(Registers.irisLike(ModChecker.NEOCULUS)),
		PHOTONICS(Registers.classes("at.redi2go.photonics.client.Photonics", "at.redi2go.photonic.client.Photonic")),
		ASTROCRAFT(Registers.classes("mod.lwhrvw.astrocraft.Astrocraft")),
		OPTIFINE(Registers.classes("optifine.OptiFineTweaker")),
		FABRIC_SKYBOXES(Registers.classes("io.github.amerebagatelle.fabricskyboxes.SkyboxManager", "me.flashyreese.mods.nuit.SkyboxManager")),
		FORGE_SKYBOXES(Registers.classes("com.foopy.forgeskyboxes.SkyboxManager")),
		SKYBOXIFY(Registers.classes("btw.lowercase.skyboxify.SkyboxifyClient", "btw.lowercase.optiboxes.OptiBoxesClient")),
		STELLAR_VIEW(Registers.classes("net.povstalec.stellarview.StellarView")),
		CELESTIAL(Registers.classes("fishcute.celestial.Celestial")),
		HORIZON(Registers.classes("com.jeff.horizon.SkyboxManager")),
		SPYGLASS_ASTRONOMY(Registers.classes("com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient")),
		THREE_D_SKIN_LAYERS(Registers.classes("dev.tr7zw.skinlayers.SkinLayersMod")),
		CAELUM(Registers.classes("nuparu.caelum.Caelum", "space.ajcool.Caelum")),
		SERENE_SEASONS(Registers.classes("sereneseasons.api.season.SeasonHelper")),
		FABRIC_SEASONS(Registers.classes("io.github.lucaargolo.seasons.FabricSeasons")),
		ECLIPTIC_SEASONS(Registers.classes("com.teamtea.eclipticseasons.api.EclipticSeasonsApi"));

		private final Registers register;

		ModNames(Registers register) {
			this.register = register;
		}

		boolean isPresent() {
			return this.register.isPresent();
		}

		public String[] getClassNames() {
			return this.register.getClassNames();
		}
	}

	private static final EnumMap<ModNames, Boolean> modNameCache = new EnumMap<>(ModNames.class);

	private static final class Registers {
		private final String shaderLoaderId;
		private final String[] classNames;

		private Registers(String shaderLoaderId, String... classNames) {
			this.shaderLoaderId = shaderLoaderId;
			this.classNames = classNames;
		}

		static Registers classes(String... classNames) {
			return new Registers(null, classNames);
		}

		static Registers irisLike(String shaderLoaderId) {
			return new Registers(shaderLoaderId, ModChecker.MODERN_IRIS_CLASS, ModChecker.LEGACY_IRIS_CLASS);
		}

		boolean isPresent() {
			if (shaderLoaderId != null) {
				return shaderLoaderId.equals(getIrisLikeLoader());
			}
			return hasAnyClass(classNames);
		}

		String[] getClassNames() {
			return classNames;
		}
	}

	private static void debugLog(String message) {
		EuphoriaLogger.debugLog("[ModChecker] " + message);
	}

	public static boolean isModPresent(ModNames modName) {
		Boolean cached = modNameCache.get(modName);
		if (cached != null) {
			debugLog("Cached mod found for " + modName + ": " + cached);
			return cached;
		}
		boolean present = modName.isPresent();
		modNameCache.put(modName, present);
		return present;
	}

	public static String[] getClassNames(ModNames modName) {
		return modName.getClassNames();
	}

	private static boolean hasAnyClass(String... classNames) {
		for (String className : classNames) {
			if (ReflectionUtils.checkClassExists(className)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Attempts to find the Iris/Oculus/Angelica class from known possible locations.
	 *
	 * @return The Iris class if found, null otherwise
	 */
	public static Class<?> findIrisClass() {
		if (irisClassSearched) {
			return cachedIrisClass;
		}

		irisClassSearched = true;

		if (ReflectionUtils.checkClassExists(MODERN_IRIS_CLASS)) {
			try {
				cachedIrisClass = Class.forName(MODERN_IRIS_CLASS);
				return cachedIrisClass;
			} catch (ClassNotFoundException e) {
				debugLog("Class exists but couldn't load: " + MODERN_IRIS_CLASS);
			}
		}

		if (ReflectionUtils.checkClassExists(LEGACY_IRIS_CLASS)) {
			try {
				cachedIrisClass = Class.forName(LEGACY_IRIS_CLASS);
				return cachedIrisClass;
			} catch (ClassNotFoundException e) {
				debugLog("Class exists but couldn't load: " + LEGACY_IRIS_CLASS);
			}
		}

		cachedIrisClass = null;
		return null;
	}

	private static String getModIdFromIrisClass(Class<?> classObj) {
		try {
			try {
				java.lang.reflect.Field modNameField = classObj.getField("MODNAME");
				Object modNameValue = modNameField.get(null);
				if (modNameValue instanceof String) {
					String modName = (String) modNameValue;
					debugLog("Found MODNAME field in " + classObj.getName() + ": " + modName);

					switch (modName) {
						case "Iris":
							debugLog("Detected Iris via MODNAME='Iris'");
							return IRIS;
						case "Oculus":
						case "Emberlens":
							debugLog("Detected Oculus via MODNAME='" + modName + "'");
							return OCULUS;
						case "AngelicaShaders":
							debugLog("Detected Angelica via MODNAME='AngelicaShaders'");
							return ANGELICA;
						case "NeOculus":
							debugLog("Detected NeOculus  via MODNAME='NeOculus'");
							return NEOCULUS;
					}
				}
			} catch (NoSuchFieldException e) {
				debugLog("MODNAME field not found");
			}

		} catch (Exception e) {
			debugLog("Error reading fields from " + classObj.getName() + ": " + e.getMessage());
		}
		return null;
	}

	/**
	 * Gets the shader loader ID for an Iris-like class.
	 *
	 * @return The shader loader ID (iris/oculus/angelica), or null if not found
	 */
	private static String getIrisLikeLoader() {
		if (cachedIrisLikeLoader != null) {
			return cachedIrisLikeLoader;
		}

		Class<?> irisClass = findIrisClass();
		if (irisClass != null) {
			String modId = getModIdFromIrisClass(irisClass);
			if (IRIS.equals(modId)) {
				debugLog("Detected IRIS via class check and MODNAME field");
				cachedIrisLikeLoader = IRIS;
				return cachedIrisLikeLoader;
			} else if (OCULUS.equals(modId)) {
				debugLog("Detected OCULUS via class check and MODNAME field");
				cachedIrisLikeLoader = OCULUS;
				return cachedIrisLikeLoader;
			} else if (ANGELICA.equals(modId)) {
				debugLog("Detected ANGELICA via class check and MODNAME field");
				cachedIrisLikeLoader = ANGELICA;
				return cachedIrisLikeLoader;
			}  else if (NEOCULUS.equals(modId)) {
				debugLog("Detected NEOCULUS via class check and MODNAME field");
				cachedIrisLikeLoader = NEOCULUS;
				return cachedIrisLikeLoader;
			} else {
				debugLog("Found Iris-like class but MODNAME was: " + modId);
				debugLog("Assuming IRIS as default for Iris-like class");
				cachedIrisLikeLoader = IRIS;
				return cachedIrisLikeLoader;
			}
		}

		cachedIrisLikeLoader = null;
		return null;
	}

	/**
	 * Detects shader loader by checking for known classes.
	 *
	 * @return shader loader id (iris/oculus/angelica/optifine), or null if not detected
	 */
	public static String detectShaderLoaderByClass() {
		debugLog("Attempting shader loader detection via class checks");

		String irisLikeLoader = getIrisLikeLoader();
		if (irisLikeLoader != null) {
			return irisLikeLoader;
		}

		if (isModPresent(ModNames.OPTIFINE)) {
			debugLog("Detected OPTIFINE via class check");
			return OPTIFINE;
		}

		debugLog("No shader loader detected via class checks");
		return null;
	}
}
