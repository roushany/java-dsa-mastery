package com.javadsamastery.algorithm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BSOnAnswerAlgorithmTest {

    // Optimized Implementation
    public long splitArray(int[] tasks, int k) {
        if (tasks == null || tasks.length == 0 || k <= 0) return 0;
        long left = 0, right = 0;
        for (int task : tasks) {
            left = Math.max(left, (long) task);
            right += task;
        }
        while (left < right) {
            long mid = left + (right - left) / 2;
            if (canSplit(tasks, k, mid)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        return left;
    }

    private boolean canSplit(int[] tasks, int k, long maxLoad) {
        int currentWorkers = 1;
        long currentLoad = 0;
        for (int task : tasks) {
            if (task > maxLoad) return false;

            if (currentLoad + task > maxLoad) {
                currentWorkers++;
                currentLoad = task;
                if (currentWorkers > k) return false;
            } else {
                currentLoad += task;
            }
        }
        return true;
    }

    // Brute-force reference implementation for validation (test-only)
    private long bruteForceSplitArray(int[] tasks, int k) {
        if (tasks == null || tasks.length == 0 || k <= 0) return 0;
        return dfs(tasks, k, 0);
    }

    private long dfs(int[] tasks, int k, int startIndex) {
        if (k == 1) {
            long sum = 0;
            for (int i = startIndex; i < tasks.length; i++) {
                sum += tasks[i];
            }
            return sum;
        }
        if (startIndex == tasks.length) {
            return Long.MAX_VALUE / 2; // Invalid, used up tasks but need more workers
        }

        long minMaxSum = Long.MAX_VALUE;
        long currentSum = 0;

        for (int i = startIndex; i <= tasks.length - k; i++) {
            currentSum += tasks[i];
            long restMax = dfs(tasks, k - 1, i + 1);
            long currentMax = Math.max(currentSum, restMax);
            minMaxSum = Math.min(minMaxSum, currentMax);
        }
        return minMaxSum;
    }

    private void assertMatch(int[] tasks, int k) {
        assertEquals(bruteForceSplitArray(tasks, k), splitArray(tasks, k), "Optimized must match Brute Force");
    }

    @Test
    void testCorrectnessAgainstBruteForce() {
        assertMatch(new int[]{7, 2, 5, 10, 8}, 2);
        assertMatch(new int[]{5}, 1); // Single element
        assertMatch(new int[]{1, 2, 3}, 1); // One worker
        assertMatch(new int[]{1, 2, 3}, 3); // One worker per task
        assertMatch(new int[]{2, 2, 2, 2}, 2); // All equal values
        assertMatch(new int[]{1, 2, 3, 4, 5}, 2); // Increasing
        assertMatch(new int[]{5, 4, 3, 2, 1}, 2); // Decreasing
        assertMatch(new int[]{10, 1, 1, 1}, 2); // Optimum exactly lower bound (10)
    }

    @Test
    void testLargeValuesOverflow() {
        int[] large = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE};
        long expected = 2L * Integer.MAX_VALUE;
        assertEquals(expected, splitArray(large, 1), "Must handle values exceeding Integer.MAX_VALUE");
    }

    @Test
    void testInvalidInputs() {
        assertEquals(0, splitArray(new int[]{}, 2), "Empty array");
        assertEquals(0, splitArray(new int[]{1, 2, 3}, 0), "Invalid K");
    }
}
