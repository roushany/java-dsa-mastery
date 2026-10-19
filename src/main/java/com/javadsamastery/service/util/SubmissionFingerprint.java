package com.javadsamastery.service.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SubmissionFingerprint {

    private static final String FP_PREFIX = "\n<!-- idemp:";
    private static final String FP_SUFFIX = " -->";

    private SubmissionFingerprint() {}

    public static String computeFingerprint(String attemptId, String answer) {
        String normalizedAnswer = answer != null ? answer.trim().replaceAll("\\s+", " ") : "";
        String payload = attemptId + ":" + normalizedAnswer;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String attachFingerprint(String feedback, String fingerprint) {
        String baseFeedback = feedback != null ? feedback : "";
        return baseFeedback + FP_PREFIX + fingerprint + FP_SUFFIX;
    }

    public static String extractFingerprint(String feedback) {
        if (feedback == null) return null;
        int start = feedback.lastIndexOf(FP_PREFIX);
        if (start < 0) return null;
        int end = feedback.indexOf(FP_SUFFIX, start + FP_PREFIX.length());
        if (end < 0) return null;
        return feedback.substring(start + FP_PREFIX.length(), end).trim();
    }

    public static String cleanFeedback(String feedback) {
        if (feedback == null) return null;
        int start = feedback.lastIndexOf(FP_PREFIX);
        if (start < 0) return feedback;
        return feedback.substring(0, start).trim();
    }
}
