package ssatr.device;

import ssatr.service.MqttConnection;

import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String commandStr = commands.take();
                String[] parts = commandStr.split(" ");
                String cmd = parts[0];
                long now = System.currentTimeMillis();
                String responseText = "OK " + commandStr;

                try {
                    switch (cmd) {
                        case "SET_INTERVAL":
                            periodic.setPeriodMs(Long.parseLong(parts[1]));
                            break;
                        case "SET_THRESHOLD":
                            periodic.setThreshold(Double.parseDouble(parts[1]));
                            break;
                        case "RESET":
                            sensor.reset();
                            break;
                        case "STATUS":
                            responseText = String.format(java.util.Locale.US, "OK PERIOD=%d THRESHOLD=%.2f",
                                    periodic.getPeriodMs(), periodic.getThreshold());
                            break;
                        default:
                            responseText = "ERROR Comandă necunoscută: " + cmd;
                    }
                } catch (Exception e) {
                    responseText = "ERROR Parametri incorecți pentru: " + cmd;
                }

                String payload = String.format(java.util.Locale.US, "%s;%d;%s", deviceId, now, responseText);
                mqtt.publish(Config.statusTopic(deviceId), payload);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}