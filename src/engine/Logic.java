package engine;

import org.joml.Vector3f;

@SuppressWarnings("unused")
public final class Logic {
    public static float remap(Number minA, Number maxA, Number minB, Number maxB, Number value) {
        return minB.floatValue() + (value.floatValue() - minA.floatValue()) * (maxB.floatValue() - minB.floatValue()) / (maxA.floatValue() - minA.floatValue());
    }
    public static float remapClamped(Number minA, Number maxA, Number minB, Number maxB, Number value) {
        return clamp(remap(minA, maxA, minB, maxB, value), Math.min(minB.doubleValue(), maxB.doubleValue()), Math.max(minB.doubleValue(), maxB.doubleValue()));
    }
    public static Vector3f remapClamped(Number minA, Number maxA, Number minB, Number maxB, Vector3f value) {
        return new Vector3f(remapClamped(minA, maxA, minB, maxB, value.x), remapClamped(minA, maxA, minB, maxB, value.y), remapClamped(minA, maxA, minB, maxB, value.z));
    }

    public static float clamp(Number value, Number min, Number max) { return Math.max(min.floatValue(), Math.min(max.floatValue(), value.floatValue())); }

    public static double random(Number range) { return random(0, range); }
    public static double random(Number min, Number max) { return Math.random() * (max.doubleValue() - min.doubleValue()) + min.doubleValue(); }

    public static int iRandom(Number range) { return (int) random(range); }
    public static int iRandom(Number min, Number max) { return (int) random(min, max); }
}
