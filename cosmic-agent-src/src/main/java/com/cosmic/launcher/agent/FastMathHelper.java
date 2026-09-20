package com.cosmic.launcher.agent;

/**
 * Lunar Client / Badlion Client style high-performance Fast Math utilities.
 * Replaces expensive floating-point trigonometric calculations with pre-computed lookup tables.
 */
public final class FastMathHelper {
    private static final float[] SIN_TABLE = new float[65536];
    private static final int SIN_MASK = 65535;
    private static final float RAD_TO_INDEX = 10430.378f; // (65536 / (2 * PI))

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
        }
    }

    public static float sin(float rad) {
        return SIN_TABLE[(int) (rad * RAD_TO_INDEX) & SIN_MASK];
    }

    public static float cos(float rad) {
        return SIN_TABLE[(int) (rad * RAD_TO_INDEX + 16384.0f) & SIN_MASK];
    }

    public static int fastFloor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    public static int fastCeil(double value) {
        int i = (int) value;
        return value > (double) i ? i + 1 : i;
    }

    public static float fastSqrt(float value) {
        return (float) Math.sqrt(value);
    }
}
