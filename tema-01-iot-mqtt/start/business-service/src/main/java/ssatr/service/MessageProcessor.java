package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = inbox.take();
                storage.append(kind, message);

                String[] parts = message.split(";");
                if (parts.length < 3) continue; // mesaj invalid, trecem mai departe

                String deviceId = parts[0];
                long timestamp = Long.parseLong(parts[1]);

                switch (kind) {
                    case "telemetry":
                        dashboard.updateTelemetry(deviceId, timestamp, parts[2]);
                        break;
                    case "alarm":
                        dashboard.updateAlarm(deviceId, timestamp);
                        System.out.println("ALARMĂ PRIMITĂ de la " + deviceId + ": Temp=" + parts[2] + " Prag=" + parts[3]);
                        break;
                    case "status":
                        dashboard.updateStatus(deviceId, timestamp);
                        System.out.println("STATUS PRIMIT de la " + deviceId + ": " + parts[2]);
                        break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Dacă apare o eroare de format (ex: parseLong dă fail),
                // nu oprim thread-ul, o ignorăm
                System.out.println("Eroare la procesare mesaj (" + kind + "): " + e.getMessage());
            }
        }
    }
}