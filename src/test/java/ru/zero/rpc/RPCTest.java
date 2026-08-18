package ru.zero.rpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RPCTest {

    @Test
    public void testNativeLibraryAvailable() {
        // Собственный IPC-клиент не требует нативной библиотеки.
        assertTrue(RPC.isDiscordRPCAvailable(), "Discord RPC должен быть доступен");
    }

    @Test
    public void testRPCLifecycle() {
        RPC rpc = new RPC();

        // startRpc() запускает фоновый поток и не блокирует вызывающий.
        rpc.startRpc();
        assertTrue(rpc.isRunning(), "Discord RPC должен быть запущен");

        // Подключение асинхронное: без запущенного Discord соединения не будет,
        // и это не должно приводить к ошибке.
        rpc.stopRpc();
        assertFalse(rpc.isRunning(), "Discord RPC должен быть остановлен");
        assertFalse(rpc.isInitialized(), "После остановки соединение должно быть закрыто");
    }

    @Test
    public void testStopWithoutStartIsSafe() {
        RPC rpc = new RPC();
        rpc.stopRpc();
        assertFalse(rpc.isRunning(), "Повторная остановка не должна падать");
    }
}
