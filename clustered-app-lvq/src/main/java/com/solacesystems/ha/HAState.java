package com.solacesystems.ha;

/**
 * Represents the application cluster member HA state
 */
public enum HAState {
    /**
     * The instance is connected to the cluster as the Hot Backup
     */
    BACKUP,
    /**
     * The instance is connected to the cluster as the Active member responsible for outputting application state
     */
    ACTIVE
}
