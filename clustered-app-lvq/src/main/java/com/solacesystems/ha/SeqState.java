package com.solacesystems.ha;


/**
 * Represents the application cluster member state with respect to the input stream sequence.
 */
public enum SeqState {
    /**
     * The instance is in initial state, before connecting, binding, or reading
     */
    INIT,
    /**
     * The instance lost its connection and is trying to reconnect
     */
    DISCONNECTED,
    /**
     * The instance is connected to the cluster but has not yet bound to queues
     */
    CONNECTED,
    /**
     * The instance is connected to the cluster and recovering from the last-value queue & app-queue
     */
    RECOVERING,
    /**
     * The instance is connected to the cluster and it's application state is caught up with the input flow.
     */
    UPTODATE
}
