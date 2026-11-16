package ssatr.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dashboard {

    private static class DeviceState {
        double lastTemp;
        long lastMessageTime;
        int alarmCount;
    }

    private final Map<String, DeviceState> devices = new ConcurrentHashMap<>();

    public void updateTelemetry(String deviceId, long timestamp, double temp) {
        devices.compute(deviceId, (id, state) -> {
            if (state == null) state = new DeviceState();
            state.lastTemp = temp;
            state.lastMessageTime = timestamp;
            return state;
        });
    }

    public void updateAlarm(String deviceId, long timestamp) {
        devices.compute(deviceId, (id, state) -> {
            if (state == null) state = new DeviceState();
            state.alarmCount++;
            state.lastMessageTime = timestamp;
            return state;
        });
    }

    public void updateStatus(String deviceId, long timestamp) {
        devices.compute(deviceId, (id, state) -> {
            if (state == null) state = new DeviceState();
            state.lastMessageTime = timestamp;
            return state;
        });
    }

    public void print() {
        System.out.printf("%n%-15s %-10s %-25s %-10s%n", "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME");
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            String id = entry.getKey();
            DeviceState s = entry.getValue();
            long secondsAgo = (now - s.lastMessageTime) / 1000;
            System.out.printf("%-15s %-10.2f acum %d s %15d%n", id, s.lastTemp, secondsAgo, s.alarmCount);
        }
        System.out.println();
    }
}