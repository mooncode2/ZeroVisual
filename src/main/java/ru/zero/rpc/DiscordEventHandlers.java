package ru.zero.rpc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Event handlers structure for Discord GameSDK Rich Presence
 * Based on official Discord GameSDK API
 */
@Environment(EnvType.CLIENT)
public class DiscordEventHandlers {
    
    /**
     * Called when Discord RPC is ready and connected
     * @param userId User ID
     * @param username Username
     * @param discriminator Discriminator (e.g., #1234)
     * @param avatar Avatar hash
     */
    public void ready(String userId, String username, String discriminator, String avatar) {
        System.out.println("[ZeroDLC] Discord RPC ready! User: " + username + "#" + discriminator);
    }
    
    /**
     * Called when Discord RPC is disconnected
     * @param errorCode Error code
     * @param message Error message
     */
    public void disconnected(int errorCode, String message) {
        System.err.println("[ZeroDLC] Discord RPC disconnected: " + message + " (code: " + errorCode + ")");
    }
    
    /**
     * Called when an error occurs in Discord RPC
     * @param errorCode Error code
     * @param message Error message
     */
    public void errored(int errorCode, String message) {
        System.err.println("[ZeroDLC] Discord RPC error: " + message + " (code: " + errorCode + ")");
    }
    
    /**
     * Called when someone requests to join the game
     * @param userId User ID
     * @param username Username
     * @param discriminator Discriminator
     * @param avatar Avatar hash
     */
    public void joinRequest(String userId, String username, String discriminator, String avatar) {
        System.out.println("[ZeroDLC] Join request from: " + username + "#" + discriminator);
    }
}