package ssatr.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {

    private static class DeviceState {
        String lastTemp = "-";
        long lastMessageTime = 0;
        int alarmCount = 0;
    }

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    private DeviceState getState(String deviceId) {
        return states.computeIfAbsent(deviceId, k -> new DeviceState());
    }

    public void updateTelemetry(String deviceId, long timestamp, String temp) {
        DeviceState state = getState(deviceId);
        state.lastTemp = temp;
        state.lastMessageTime = timestamp;
    }

    public void updateAlarm(String deviceId, long timestamp) {
        DeviceState state = getState(deviceId);
        state.alarmCount++;
        state.lastMessageTime = timestamp;
    }

    public void updateStatus(String deviceId, long timestamp) {
        DeviceState state = getState(deviceId);
        state.lastMessageTime = timestamp;
    }

    /** Afișează un tabel cu starea tuturor device-urilor. */
    public void print() {
        System.out.println("DEVICE\t\tTEMP\tULTIMUL MESAJ\tALARME");
        long now = System.currentTimeMillis();

        for (Map.Entry<String, DeviceState> entry : states.entrySet()) {
            String deviceId = entry.getKey();
            DeviceState state = entry.getValue();

            String age = (state.lastMessageTime == 0) ? "niciodata" :
                    "acum " + ((now - state.lastMessageTime) / 1000) + " s";

            System.out.printf("%s\t%s\t%s\t%d%n", deviceId, state.lastTemp, age, state.alarmCount);
        }
        if (states.isEmpty()) {
            System.out.println("(niciun device înregistrat)");
        }
    }
}