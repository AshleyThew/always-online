package me.dablakbandit.ao.utils;

import java.util.UUID;

public class UUIDUtil {
    
    /**
     * Validates if a string is a valid UUID format
     * @param uuidString The string to validate
     * @return true if the string is a valid UUID, false otherwise
     */
    public static boolean isValidUUID(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
