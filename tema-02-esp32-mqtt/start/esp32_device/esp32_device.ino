#include <Arduino.h>
#include "config.h"
#include "connection.h"

// Variables must be volatile because they are accessed across different tasks
volatile uint32_t current_period = PERIOD_MS_INITIAL;
volatile float current_threshold = THRESHOLD_INITIAL;

// Queue definitions
QueueHandle_t alarm_queue;
QueueHandle_t command_queue;

struct AlarmEvent {
    float temperature;
    float threshold;
};

struct CommandEvent {
    char text[COMMAND_MAX_LEN];
};

float read_temperature() {
    return temperatureRead();
}

// Called by the MQTT client task. Put into queue and return immediately.
void command_received(const char* text) {
    CommandEvent cmd;
    strncpy(cmd.text, text, COMMAND_MAX_LEN - 1);
    cmd.text[COMMAND_MAX_LEN - 1] = '\0';
    xQueueSend(command_queue, &cmd, 0); 
}

// TODO 1: Periodic task
void periodicTask(void* pvParameters) {
    TickType_t last = xTaskGetTickCount();
    for (;;) {
        float temp = read_temperature();
        
        char payload[128];
        snprintf(payload, sizeof(payload), "%s;%lld;%.2f", DEVICE_ID, (long long)epoch_ms(), temp);
        mqtt_publish("telemetry", payload);

        if (temp > current_threshold) {
            AlarmEvent alarm = {temp, current_threshold};
            xQueueSend(alarm_queue, &alarm, 0); // Non-blocking
        }

        xTaskDelayUntil(&last, pdMS_TO_TICKS(current_period));
    }
}

// TODO 2: Sporadic task
void sporadicTask(void* pvParameters) {
    TickType_t last_alarm = 0;
    for (;;) {
        AlarmEvent alarm;
        if (xQueueReceive(alarm_queue, &alarm, portMAX_DELAY)) {
            TickType_t now = xTaskGetTickCount();
            
            // Enforce MIN_INTERARRIVAL_MS
            if (last_alarm == 0 || (now - last_alarm) >= pdMS_TO_TICKS(MIN_INTERARRIVAL_MS)) {
                char payload[128];
                snprintf(payload, sizeof(payload), "%s;%lld;%.2f;%.2f", DEVICE_ID, (long long)epoch_ms(), alarm.temperature, alarm.threshold);
                mqtt_publish("alarm", payload);
                last_alarm = now;
                Serial.println("Alarma trimisa la broker!");
            } else {
                Serial.println("Alarma respinsa: se incalca MIN_INTERARRIVAL_MS");
            }
        }
    }
}

// TODO 3: Aperiodic task
void aperiodicTask(void* pvParameters) {
    for (;;) {
        CommandEvent cmd;
        if (xQueueReceive(command_queue, &cmd, portMAX_DELAY)) {
            char response[128] = "OK ";
            bool valid = true;

            if (strncmp(cmd.text, "SET_INTERVAL ", 13) == 0) {
                current_period = atoi(cmd.text + 13);
            } else if (strncmp(cmd.text, "SET_THRESHOLD ", 14) == 0) {
                current_threshold = atof(cmd.text + 14);
            } else if (strcmp(cmd.text, "RESET") == 0) {
                current_period = PERIOD_MS_INITIAL;
                current_threshold = THRESHOLD_INITIAL;
            } else if (strcmp(cmd.text, "STATUS") == 0) {
                snprintf(response, sizeof(response), "OK STATUS T=%u TH=%.2f", current_period, current_threshold);
            } else {
                snprintf(response, sizeof(response), "ERROR comanda invalida");
                valid = false;
            }

            if (valid && strcmp(cmd.text, "STATUS") != 0) {
                strncat(response, cmd.text, sizeof(response) - strlen(response) - 1);
            }

            char payload[256];
            snprintf(payload, sizeof(payload), "%s;%lld;%s", DEVICE_ID, (long long)epoch_ms(), response);
            mqtt_publish("status", payload);
            Serial.printf("Comanda procesata: %s\n", cmd.text);
        }
    }
}

// TODO 0: setup
void setup() {
    Serial.begin(115200);
    Serial.setTxTimeoutMs(0); // Must remain 0 to avoid blocking when serial is disconnected

    wifi_connect();
    time_sync();

    // Create queues before mqtt_start
    alarm_queue = xQueueCreate(10, sizeof(AlarmEvent));
    command_queue = xQueueCreate(10, sizeof(CommandEvent));

    mqtt_start();

    // Create tasks after mqtt_start with priorities 2-19 and 4096 stack
    xTaskCreate(periodicTask, "Periodic", 4096, NULL, 5, NULL);
    xTaskCreate(sporadicTask, "Sporadic", 4096, NULL, 6, NULL);
    xTaskCreate(aperiodicTask, "Aperiodic", 4096, NULL, 4, NULL);
}

void loop() {
    vTaskDelete(NULL); // Remove loop task to free resources
}