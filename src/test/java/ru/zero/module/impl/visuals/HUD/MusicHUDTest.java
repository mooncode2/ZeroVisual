package ru.zero.module.impl.visuals.HUD;

import org.junit.jupiter.api.Test;
import ru.zero.util.client.MusicPlayer;
import ru.zero.util.render.core.Renderer2DInterface;
import ru.zero.util.render.text.FontObject;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.text.TextRenderer;
import static org.mockito.Mockito.*;

public class MusicHUDTest {

    @Test
    public void testMusicWidgetHidesWhenNotPlaying() {
        // Arrange
        Renderer2DInterface mockRenderer = mock(Renderer2DInterface.class);
        MusicPlayer.updateFromPlayer("none"); // No music playing

        // Act
        MusicHUD.musicWidget(mockRenderer);

        // Assert
        // Widget should not draw anything when music is not playing
        verify(mockRenderer, never()).rect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
        verify(mockRenderer, never()).text(any(), anyFloat(), anyFloat(), anyFloat(), anyString(), anyInt());
    }

    @Test
    public void testMusicWidgetShowsWhenPlaying() {
        // Arrange
        Renderer2DInterface mockRenderer = mock(Renderer2DInterface.class);
        
        // Setup mock to return non-null TextMetrics for any text measurement
        TextRenderer.TextMetrics mockMetrics = new TextRenderer.TextMetrics(100.0F, 20.0F);
        when(mockRenderer.measureText(isNull(), anyString(), anyFloat())).thenReturn(mockMetrics);
        
        // Also setup mock for texture drawing to avoid NullPointerException
        doNothing().when(mockRenderer).texture(anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
        
        MusicPlayer.updateFromPlayer("yandex"); // Simulate Yandex Music playing
        
        // Ensure music is playing
        assert MusicPlayer.isPlaying() : "Music should be playing after updateFromPlayer";
        
        // Act
        try {
            MusicHUD.musicWidget(mockRenderer);
        } catch (Exception e) {
            System.err.println("Exception during musicWidget execution:");
            e.printStackTrace();
            throw e;
        }
        
        // Assert
        // Widget should draw background, text, and controls when music is playing
        verify(mockRenderer, atLeastOnce()).rect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
        verify(mockRenderer, atLeastOnce()).text(isNull(), anyFloat(), anyFloat(), anyFloat(), anyString(), anyInt());
    }
}