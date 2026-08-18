package ru.zero.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Утилита для загрузки нативных библиотек из ресурсов JAR
 * Автоматически определяет платформу и загружает соответствующую библиотеку
 */
public class NativeLibraryLoader {
    
    private static final String LIBRARY_NAME = "discord_game_sdk";
    private static final String RESOURCE_PATH = "/discord_game_sdk/";
    
    /**
     * Загружает нативную библиотеку из ресурсов JAR
     * @return true, если библиотека успешно загружена, false - если произошла ошибка
     */
    public static boolean loadLibraryFromJar() {
        try {
            String libraryFileName = getLibraryFileName();
            String libraryPath = RESOURCE_PATH + libraryFileName;
            
            // Проверяем, доступна ли библиотека в ресурсах
            try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream(libraryPath)) {
                if (inputStream == null) {
                    System.err.println("[ZeroDLC] Нативная библиотека не найдена в ресурсах: " + libraryPath);
                    return false;
                }
            }
            
            // Создаем временный файл для библиотеки
            Path tempDir = Files.createTempDirectory("zero_dlc_native_libs");
            File tempLibraryFile = new File(tempDir.toFile(), libraryFileName);
            tempLibraryFile.deleteOnExit();
            
            // Копируем библиотеку из ресурсов во временный файл
            try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream(libraryPath);
                 FileOutputStream outputStream = new FileOutputStream(tempLibraryFile)) {
                
                if (inputStream == null) {
                    System.err.println("[ZeroDLC] Не удалось получить поток для библиотеки: " + libraryPath);
                    return false;
                }
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            // Загружаем библиотеку из временного файла
            System.load(tempLibraryFile.getAbsolutePath());
            System.out.println("[ZeroDLC] Нативная библиотека успешно загружена из ресурсов: " + libraryFileName);
            
            // Планируем удаление временных файлов при выходе JVM
            tempDir.toFile().deleteOnExit();
            return true;
            
        } catch (IOException e) {
            System.err.println("[ZeroDLC] Ошибка при копировании нативной библиотеки: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SecurityException e) {
            System.err.println("[ZeroDLC] Ошибка безопасности при загрузке нативной библиотеки: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[ZeroDLC] Не удалось загрузить нативную библиотеку: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Определяет имя файла библиотеки на основе текущей платформы
     * @return имя файла библиотеки (например, "discord_game_sdk.dll")
     */
    private static String getLibraryFileName() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        
        if (osName.contains("win")) {
            if (arch.contains("64") || arch.equals("x86_64") || arch.equals("amd64")) {
                return "discord_game_sdk.dll";
            } else {
                return "discord_game_sdk.dll"; // 32-bit Windows
            }
        } else if (osName.contains("linux")) {
            if (arch.contains("64") || arch.equals("x86_64") || arch.equals("amd64")) {
                return "libdiscord_game_sdk.so";
            } else {
                return "libdiscord_game_sdk.so"; // 32-bit Linux
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "libdiscord_game_sdk.dylib";
        }
        
        // По умолчанию для Windows x64
        return "discord_game_sdk.dll";
    }
    
    /**
     * Проверяет, доступна ли библиотека для текущей платформы
     * @return true, если библиотека доступна для текущей платформы
     */
    public static boolean isLibraryAvailable() {
        String libraryFileName = getLibraryFileName();
        String libraryPath = RESOURCE_PATH + libraryFileName;
        
        try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream(libraryPath)) {
            return inputStream != null;
        } catch (IOException e) {
            return false;
        }
    }
}