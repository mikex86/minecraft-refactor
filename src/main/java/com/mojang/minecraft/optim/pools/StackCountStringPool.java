package com.mojang.minecraft.optim.pools;

public class StackCountStringPool {

    private static final String[] STACK_COUNTS = new String[64];

    static {
        for (int i = 0; i < STACK_COUNTS.length; i++) {
            STACK_COUNTS[i] = String.valueOf(i + 1);
        }
    }

    public static String valueOf(int count) {
        if (count < 1 || count > 64) {
            return String.valueOf(count);
        }
        return STACK_COUNTS[count - 1];
    }
}
