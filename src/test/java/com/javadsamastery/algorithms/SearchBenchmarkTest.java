package com.javadsamastery.algorithms;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBenchmarkTest {

    /**
     * PERFORMANCE BENCHMARK
     * Verifies that the optimized algorithm is strictly faster on large datasets.
     */
    @Test
    void testBinarySearchIsFasterThanLinearSearch() {
        int size = 5_000_000;
        int[] arr = IntStream.range(0, size).toArray();
        int target = size - 1; // Worst case for linear search
        
        // JVM Warmup
        LinearSearch.search(arr, target);
        BinarySearch.search(arr, target);
        
        long startLinear = System.nanoTime();
        LinearSearch.search(arr, target);
        long timeLinear = System.nanoTime() - startLinear;
        
        long startBinary = System.nanoTime();
        BinarySearch.search(arr, target);
        long timeBinary = System.nanoTime() - startBinary;
        
        assertTrue(timeBinary < timeLinear, 
            "Binary search (" + timeBinary + "ns) should be faster than linear search (" + timeLinear + "ns)");
    }
}
