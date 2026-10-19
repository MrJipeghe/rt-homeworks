package ssatr.device;

import ssatr.service.MqttConnection;

import java.util.concurrent.BlockingQueue;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<Double> alarmEvents) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        long lastReleaseTime = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Double temp = alarmEvents.take();
                long now = System.currentTimeMillis();

                if (now - lastReleaseTime < MIN_INTERARRIVAL_MS) {
                    System.out.println("Alarmă respinsă - interval prea mic: " + temp);
                    continue;
                }

                lastReleaseTime = now;
                double defaultThreshold = 28.0;
                String payload = String.format(java.util.Locale.US, "%s;%d;%.2f;%.2f",
                        deviceId, now, temp, defaultThreshold);
                mqtt.publish(Config.alarmTopic(deviceId), payload);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}