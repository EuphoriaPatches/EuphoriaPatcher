package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

// Backend-agnostic sink for uniform declarations
public interface UniformDeclarer {

    // Update frequency of a uniform. Only for iris/oculus
    // OptiFine has no such distinction and updates everything together.
    enum Frequency {
        ONCE, PER_TICK, PER_FRAME
    }

    void uniform1b(String name, BooleanSupplier value);

    void uniform1i(String name, IntSupplier value);

    void uniform1f(String name, DoubleSupplier value);

    void uniform2f(String name, DoubleSupplier x, DoubleSupplier y);

    void uniform2i(String name, IntSupplier x, IntSupplier y);

    void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z);

    // OptiFine uploads this as an ivec4 (w = 0), Iris as an ivec3. In the shader declare ivec3 under IS_IRIS
    // and ivec4 otherwise, and always read it with .xyz
    void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z);

    void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z);

    void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w);

    // Frequency overloads
    // By default the frequency is ignored; backends that support it override these.

    default void uniform1b(String name, BooleanSupplier value, Frequency freq) {
        uniform1b(name, value);
    }

    default void uniform1i(String name, IntSupplier value, Frequency freq) {
        uniform1i(name, value);
    }

    default void uniform1f(String name, DoubleSupplier value, Frequency freq) {
        uniform1f(name, value);
    }

    default void uniform2f(String name, DoubleSupplier x, DoubleSupplier y, Frequency freq) {
        uniform2f(name, x, y);
    }

    default void uniform2i(String name, IntSupplier x, IntSupplier y, Frequency freq) {
        uniform2i(name, x, y);
    }

    default void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, Frequency freq) {
        uniform3f(name, x, y, z);
    }

    default void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z, Frequency freq) {
        uniform3i(name, x, y, z);
    }

    default void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, Frequency freq) {
        uniform3d(name, x, y, z);
    }

    default void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w, Frequency freq) {
        uniform4f(name, x, y, z, w);
    }
}
