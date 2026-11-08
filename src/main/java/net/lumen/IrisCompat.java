package net.lumen;

import net.fabricmc.loader.api.FabricLoader;

/** If an Iris shaderpack is running, it already does its own sky/fog/grading, so Lumen's screen pass steps aside. */
public final class IrisCompat {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	private static Object api;
	private static java.lang.reflect.Method inUse;

	public static boolean shaderPackActive() {
		if (!IRIS) return false;
		try {
			if (inUse == null) {
				Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = c.getMethod("getInstance").invoke(null);
				inUse = c.getMethod("isShaderPackInUse");
			}
			return (Boolean) inUse.invoke(api);
		} catch (Throwable t) {
			return false;
		}
	}

	private IrisCompat() {}
}
