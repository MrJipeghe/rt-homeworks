package ssatr.service;

/**
 * Consola operatorului: citește comenzi de la tastatură.
 *
 *   show                               afișează dashboard-ul
 *   <deviceId> <COMANDA> [parametru]   trimite o comandă unui device, de exemplu
 *                                      device-1 SET_INTERVAL 1000
 *   help                               afișează comenzile
 *   exit                               oprește serviciul
 */
public class CommandConsole implements Runnaprivate void sendCommand(String line) {
    int spaceIndex = line.indexOf(' ');
    if (spaceIndex == -1) {
        System.out.println("Eroare format: <deviceId> <COMANDA> [parametru]");
        return;
    }

    String deviceId = line.substring(0, spaceIndex);
    String command = line.substring(spaceIndex + 1);

    mqtt.publish(Config.commandTopic(deviceId), command);
    System.out.println("Comanda trimisa: '" + command + "' catre " + deviceId);
}