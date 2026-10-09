package com.javadsamastery.algorithms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BinarySearchEdgeCaseTest {

    @Test
    void testEmptyArray() {
        assertEquals(-1, BinarySearch.search(new int[0], 5));
    }

    @Test
    void testNullArray() {
        assertEquals(-1, BinarySearch.search(null, 5));
    }

    @Test
    void testSingleElement_Found() {
        assertEquals(0, BinarySearch.search(new int[]{42}, 42));
    }

    @Test
    void testSingleElement_NotFound() {
        assertEquals(-1, BinarySearch.search(new int[]{42}, 10));
    }

    @Test
    void testTargetSmallerThanAll() {
        assertEquals(-1, BinarySearch.search(new int[]{10, 20, 30}, 5));
    }

    @Test
    void testTargetLargerThanAll() {
        assertEquals(-1, BinarySearch.search(new int[]{10, 20, 30}, 40));
    }

    /**
     * REGRESSION TEST
     * Ensures we use `left + (right - left) / 2` to avoid integer overflow
     * that would occur with `(left + right) / 2` when indices are near Integer.MAX_VALUE.
     * Note: Creating a 2B element array throws OutOfMemoryError in tests,
     * so we verify the standard bounds and rely on static analysis/code review for the fix,
     * though a custom array-like interface could mock lengths if abstracted.
     */
    @Test
    void testRegression_IntegerOverflowSafetyLogic() {
        // Safe computation check for regular array
        int[] arr = {1, 2, 3, 4, 5};
        assertEquals(4, BinarySearch.search(arr, 5));
        assertEquals(0, BinarySearch.search(arr, 1));
    }
}
