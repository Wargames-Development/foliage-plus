package com.wdg.foliageplus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Small standalone runner so normal builds need no new test dependencies. */
public final class RegressionAssertions {
    public static void main(String[] args) throws Exception {
        int passed = 0, failed = 0;
        for (Class<?> type : new Class<?>[] {FoliageStabilityTest.class, FoliageTransformerTest.class, FoliageNetworkTest.class}) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().startsWith("test_")) continue;
                Object instance = type.newInstance();
                try {
                    try { type.getMethod("setup").invoke(instance); } catch (NoSuchMethodException ignored) {}
                    method.invoke(instance);
                    passed++;
                    System.out.println("PASS " + method.getName());
                } catch (InvocationTargetException e) {
                    failed++;
                    System.err.println("FAIL " + method.getName());
                    e.getCause().printStackTrace();
                }
            }
        }
        System.out.println("Foliage+ regressions: " + passed + " passed, " + failed + " failed");
        if (failed != 0) throw new AssertionError("Regression failures: " + failed);
    }

    public static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    public static void assertFalse(boolean value) { assertTrue(!value); }
    public static void assertNull(Object value) { if (value != null) throw new AssertionError("Expected null"); }
    public static void assertNotNull(Object value) { if (value == null) throw new AssertionError("Expected a value"); }
    public static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void assertEquals(double expected, double actual, double epsilon) {
        if (!(Math.abs(expected - actual) <= epsilon)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
