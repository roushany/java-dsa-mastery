package com.javadsamastery.algorithm;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HashingAlgorithmTest {

    // Implementation from topics/hashing.json
    public boolean containsDuplicate(int[] nums) {
        if (nums == null) return false;
        Set<Integer> seen = new HashSet<>();
        for (int num : nums) {
            if (!seen.add(num)) {
                return true;
            }
        }
        return false;
    }

    // Transfer problem implementation
    public int[] twoSum(int[] nums, int target) {
        if (nums == null) return new int[]{};
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }
            map.put(nums[i], i);
        }
        return new int[]{};
    }

    @Test
    void testContainsDuplicate() {
        assertTrue(containsDuplicate(new int[]{1, 2, 3, 1}), "Should detect duplicate at end");
        assertFalse(containsDuplicate(new int[]{1, 2, 3, 4}), "Should return false for unique elements");
        assertFalse(containsDuplicate(new int[]{}), "Empty array has no duplicates");
        assertFalse(containsDuplicate(null), "Null array handled safely");
        assertTrue(containsDuplicate(new int[]{0, 0}), "Zeros handled correctly");
        assertTrue(containsDuplicate(new int[]{-1, -1}), "Negative numbers handled correctly");
    }

    @Test
    void testTwoSumEdgeCases() {
        assertArrayEquals(new int[]{0, 1}, twoSum(new int[]{2, 7, 11, 15}, 9), "Standard case");
        assertArrayEquals(new int[]{1, 2}, twoSum(new int[]{3, 2, 4}, 6), "Must not use same element twice (e.g. 3+3)");
        assertArrayEquals(new int[]{0, 1}, twoSum(new int[]{3, 3}, 6), "Must handle valid duplicates correctly");
        assertArrayEquals(new int[]{}, twoSum(new int[]{1, 2, 3}, 7), "No solution");
        assertArrayEquals(new int[]{0, 2}, twoSum(new int[]{-3, 4, 3, 90}, 0), "Negative numbers");
    }
}
