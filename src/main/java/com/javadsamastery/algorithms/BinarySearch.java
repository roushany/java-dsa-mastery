package com.javadsamastery.algorithms;

/**
 * Optimized logarithmic search implementation.
 */
public class BinarySearch {
    public static int search(int[] arr, int target) {
        if (arr == null || arr.length == 0) return -1;
        
        int left = 0;
        int right = arr.length - 1;
        
        while (left <= right) {
            // Regression fix: use left + (right - left) / 2 instead of (left + right) / 2
            int mid = left + (right - left) / 2;
            
            if (arr[mid] == target) {
                return mid;
            }
            if (arr[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1;
    }
}
