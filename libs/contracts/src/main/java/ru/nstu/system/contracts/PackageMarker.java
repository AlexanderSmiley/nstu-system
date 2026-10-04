package ru.nstu.system.contracts;

/**
 * Marker type for the shared {@code ru.nstu:contracts} module.
 *
 * <p>The class is intentionally trivial: it exists so that consumers can prove,
 * at compile time, that the module is wired in through the Gradle composite
 * build ({@code includeBuild}) without publishing any artifact.</p>
 */
public final class PackageMarker {

    private PackageMarker() {
    }

    /**
     * @return the package of this shared module, e.g. {@code ru.nstu.system.contracts}
     */
    public static String moduleName() {
        return PackageMarker.class.getPackageName();
    }
}
