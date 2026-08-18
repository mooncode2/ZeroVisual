package ru.zero.rpc;

/**
 * Native interface for Discord GameSDK Rich Presence
 * Based on official Discord GameSDK API
 */
public class DiscordRPCNative {
    
    // Constants from Discord GameSDK
    public static final int DISCORD_REPLY_NO = 0;
    public static final int DISCORD_REPLY_YES = 1;
    public static final int DISCORD_REPLY_IGNORE = 2;
    
    // Activity flags
    public static final int DISCORD_ACTIVITY_FLAG_INSTANCE = 1 << 0;
    public static final int DISCORD_ACTIVITY_FLAG_JOIN = 1 << 1;
    public static final int DISCORD_ACTIVITY_FLAG_SPECTATE = 1 << 2;
    public static final int DISCORD_ACTIVITY_FLAG_JOIN_REQUEST = 1 << 3;
    public static final int DISCORD_ACTIVITY_FLAG_SYNC = 1 << 4;
    public static final int DISCORD_ACTIVITY_FLAG_PLAY = 1 << 5;
    
    // Result codes
    public static final int DISCORD_RESULT_OK = 0;
    public static final int DISCORD_RESULT_ERROR = 1;
    public static final int DISCORD_RESULT_NOT_IMPLEMENTED = 2;
    public static final int DISCORD_RESULT_INVALID_ARGUMENT = 3;
    public static final int DISCORD_RESULT_SERVICE_UNAVAILABLE = 4;
    
    /**
     * Initialize Discord RPC
     * Official signature: int64_t Discord_Initialize(const char* applicationId, DiscordEventHandlers* handlers, int autoRegister, const char* optionalSteamId);
     * 
     * @param applicationId The Discord application ID
     * @param handlers Event handlers structure
     * @param autoRegister Should automatically register the game (1 = true, 0 = false)
     * @param optionalSteamId Optional Steam ID
     * @return Result code (0 = OK, non-zero = error)
     */
    public native long Discord_Initialize(String applicationId, DiscordEventHandlers handlers, int autoRegister, String optionalSteamId);
    
    /**
     * Shutdown Discord RPC
     */
    public native void Discord_Shutdown();
    
    /**
     * Update presence
     * @param presence Rich presence structure
     */
    public native void Discord_UpdatePresence(DiscordRichPresence presence);
    
    /**
     * Run callbacks
     */
    public native void Discord_RunCallbacks();
    
    /**
     * Respond to join request
     * @param userId User ID
     * @param reply Reply type (DISCORD_REPLY_NO, DISCORD_REPLY_YES, DISCORD_REPLY_IGNORE)
     */
    public native void Discord_Respond(String userId, int reply);
    
    /**
     * Update handshake
     */
    public native void Discord_UpdateHandshake();
    
    /**
     * Clear activity
     */
    public native void Discord_ClearActivity();
}