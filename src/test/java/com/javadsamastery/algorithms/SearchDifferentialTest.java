package com.javadsamastery.algorithms;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDifferentialTest {

    /**
     * DIFFERENTIAL / PROPERTY-BASED TEST
     * Compares the optimized BinarySearch against the trusted brute-force LinearSearch
     * using thousands of randomly generated small arrays.
     */
    @Test
    void testBinarySearchVsLinearSearch() {
        Random rand = new Random(42); // Deterministic seed for reproducible tests
        int testCases = 10000;
        
        for (int i = 0; i < testCases; i++) {
            int size = rand.nextInt(100);
            int[] arr = new int[size];
            for (int j = 0; j < size; j++) {
                arr[j] = rand.nextInt(1000);
            }
            Arrays.sort(arr); // Binary search requires sorted data

            // Randomly test values that exist and values that probably don't
            int target = rand.nextBoolean() && size > 0 ? arr[rand.nextInt(size)] : rand.nextInt(1000);
            
            int linearResult = LinearSearch.search(arr, target);
            int binaryResult = BinarySearch.search(arr, target);
            
            if (linearResult == -1) {
                assertEquals(-1, binaryResult, "Binary search found an element that does not exist.");
            } else {
                // If it exists, binary search should find an index containing the target.
                // (Linear search finds first occurrence, binary search finds any occurrence).
                assertTrue(binaryResult >= 0, "Binary search failed to find an existing element.");
                assertEquals(target, arr[binaryResult], "Binary search returned wrong index value.");
            }
        }
    }
}
