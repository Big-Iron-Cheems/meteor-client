package net.minecraft.util;

/**
 * Compile-only stub of the vanilla {@code net.minecraft.util.Tuple} class, which was removed in Minecraft 26.2
 * but is still referenced by the Baritone API compiled against 26.1. Meteor never calls into it; this only lets
 * javac resolve Baritone method signatures. It is NOT bundled in the mod jar (compileOnly stub sourceSet).
 *
 * Delete this stub and the associated build-script wiring once a 26.2 Baritone build is available.
 */
public class Tuple<A, B> {
    private final A a;
    private final B b;

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}
