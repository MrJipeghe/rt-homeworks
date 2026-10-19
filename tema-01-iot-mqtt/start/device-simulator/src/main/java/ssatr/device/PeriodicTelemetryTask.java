package ssatr.device;

import ssatr.service.MqttConnection;

import java.util.concurrent.BlockingQueue;

/**
 * TASK PERIODIC, cu perioada T = periodMs: fiecare job citește senzorul și
 * publică valoarea pe topic-ul de telemetrie.
 *
 * Dacă temperatura depășește pragul, taskul NU tratează el alarma, ci doar
 * semnalează un eveniment în coada alarmEvents. Alarma este tratată de
 * taskul sporadic.
 */
public class PeriodicTelemetryTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;

    // volatile: sunt citite aici, dar modificate de taskul aperiodic (alt thread)
    private volatile long periodMs = 2000;
    private volatile double threshold = 28.0;

    public PeriodicTelemetryTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                 BlockingQueue<Double> alarmEvents) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        long nextReleaseNanos = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                double temp = sensor.read();
                long now = System.currentTimeMillis();

                String payload = String.format(java.util.Locale.US, "%s;%d;%.2f", deviceId, now, temp);
                mqtt.publish(Config.telemetryTopic(deviceId), payload);

                if (temp > threshold) {
                    alarmEvents.offer(temp);
                }

                nextReleaseNanos += periodMs * 1_000_000L;
                long sleepNanos = nextReleaseNanos - System.nanoTime();

                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void setPeriodMs(long periodMs) {
        this.periodMs = periodMs;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public long getPeriodMs() {
        return periodMs;
    }

    public double getThreshold() {
        return threshold;
    }
}