package ru.zero.rpc;

/**
 * Rich Presence structure for Discord GameSDK
 * Based on official Discord GameSDK API
 */
public class DiscordRichPresence {
    
    public String state; // What the player is doing
    public String details; // Additional details
    public long startTimestamp; // Start time in seconds
    public long endTimestamp; // End time in seconds
    public String largeImageKey; // Key for large image asset
    public String largeImageText; // Text for large image
    public String smallImageKey; // Key for small image asset
    public String smallImageText; // Text for small image
    public String partyId; // Party ID
    public int partySize; // Current party size
    public int partyMax; // Maximum party size
    public String matchId; // Match ID
    public String joinSecret; // Secret for joining
    public String spectateSecret; // Secret for spectating
    public String instance; // Instance ID
    
    // Activity flags
    public int flags;
    
    // Activity type
    public int type;
    
    // Constructor with default values
    public DiscordRichPresence() {
        this.state = "Playing";
        this.details = "Minecraft";
        this.startTimestamp = System.currentTimeMillis() / 1000L;
        this.endTimestamp = 0;
        this.largeImageKey = "minecraft";
        this.largeImageText = "Minecraft 1.21.8";
        this.smallImageKey = "";
        this.smallImageText = "";
        this.partyId = "";
        this.partySize = 0;
        this.partyMax = 0;
        this.matchId = "";
        this.joinSecret = "";
        this.spectateSecret = "";
        this.instance = "0";
        this.flags = 0;
        this.type = 0; // 0 = Playing, 1 = Streaming, 2 = Listening, 3 = Watching
    }
    
    /**
     * Set large image
     * @param key Image key
     * @param text Image text
     */
    public void setLargeImage(String key, String text) {
        this.largeImageKey = key;
        this.largeImageText = text;
    }
    
    /**
     * Set small image
     * @param key Image key
     * @param text Image text
     */
    public void setSmallImage(String key, String text) {
        this.smallImageKey = key;
        this.smallImageText = text;
    }
    
    /**
     * Set party information
     * @param id Party ID
     * @param size Current party size
     * @param max Maximum party size
     */
    public void setParty(String id, int size, int max) {
        this.partyId = id;
        this.partySize = size;
        this.partyMax = max;
    }
    
    /**
     * Set timestamps
     * @param start Start timestamp in seconds
     * @param end End timestamp in seconds (0 for no end)
     */
    public void setTimestamps(long start, long end) {
        this.startTimestamp = start;
        this.endTimestamp = end;
    }
    
    /**
     * Set secrets for joining/spectating
     * @param joinSecret Join secret
     * @param spectateSecret Spectate secret
     */
    public void setSecrets(String joinSecret, String spectateSecret) {
        this.joinSecret = joinSecret;
        this.spectateSecret = spectateSecret;
    }
    
    /**
     * Set activity flags
     * @param flags Activity flags
     */
    public void setFlags(int flags) {
        this.flags = flags;
    }
    
    /**
     * Set activity type
     * @param type Activity type (0=Playing, 1=Streaming, 2=Listening, 3=Watchin

    /**
     * Set activity type
     * @param type Activity type (0=Playing, 1=Streaming, 2=Listening, 3=Watching)
     */
    public void setType(int type) {
        this.type = type;
    }
}