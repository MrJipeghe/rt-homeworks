#include "config.h"
#include "connection.h"

enum Phase { CAR_GREEN, CAR_YELLOW, ALL_RED, PED_GREEN };
const char* const PHASE_NAMES[] = {"CAR_GREEN", "CAR_YELLOW", "ALL_RED", "PED_GREEN"};

enum Light { RED, YELLOW, GREEN };
const char* const LIGHT_NAMES[] = {"RED", "YELLOW", "GREEN"};

// TODO 0 (c): STAREA SEMAFORULUI
struct TrafficState {
    Phase phase;
    uint32_t phase_start_time;
    bool ped_request;
};

TrafficState state;
SemaphoreHandle_t state_mutex;
QueueHandle_t button_queue;

struct ButtonEvent {
    uint32_t timestamp;
};

// TODO 3: RUTINA DE ÎNTRERUPERE
void IRAM_ATTR button_isr(void) {
    ButtonEvent ev = { millis() };
    BaseType_t woken = pdFALSE;
    xQueueSendFromISR(button_queue, &ev, &woken);
    portYIELD_FROM_ISR(woken);
}

void command_received(const char* text) {
    Serial.printf("[mqtt] comandă primită: %s\n", text);
    // TODO 3: "BUTTON" merge în coada butonului
    if (strcmp(text, "BUTTON") == 0) {
        ButtonEvent ev = { millis() };
        xQueueSend(button_queue, &ev, 0); // Timeout 0, non-blocking
    }
}

// TODO 1: TASK PERIODIC DE CONTROL
void control_task(void* pvParameters) {
    TickType_t last_wake = xTaskGetTickCount();
    
    for (;;) {
        xSemaphoreTake(state_mutex, portMAX_DELAY);
        uint32_t now = millis();
        uint32_t elapsed = now - state.phase_start_time;
        Phase next_phase = state.phase;
        bool changed = false;

        switch (state.phase) {
            case CAR_GREEN:
                if (elapsed >= CAR_GREEN_MIN_MS && state.ped_request) {
                    next_phase = CAR_YELLOW;
                }
                break;
            case CAR_YELLOW:
                if (elapsed >= CAR_YELLOW_MS) {
                    next_phase = ALL_RED;
                }
                break;
            case ALL_RED:
                if (elapsed >= ALL_RED_MS) {
                    next_phase = PED_GREEN;
                    state.ped_request = false; // Se șterge cererea la intrarea în PED_GREEN
                }
                break;
            case PED_GREEN:
                if (elapsed >= PED_GREEN_MS) {
                    next_phase = CAR_GREEN;
                }
                break;
        }

        if (next_phase != state.phase) {
            state.phase = next_phase;
            state.phase_start_time = now;
            changed = true;
        }
        
        Phase current_phase_for_print = state.phase;
        xSemaphoreGive(state_mutex);

        if (changed) {
            Serial.printf("Fază nouă: %s\n", PHASE_NAMES[current_phase_for_print]);
        }

        xTaskDelayUntil(&last_wake, pdMS_TO_TICKS(CONTROL_PERIOD_MS));
    }
}

// TODO 2: TASK PERIODIC DE IEȘIRE
void output_task(void* pvParameters) {
    TickType_t last_wake = xTaskGetTickCount();

    for (;;) {
        // Copiază starea local cât mai rapid
        xSemaphoreTake(state_mutex, portMAX_DELAY);
        Phase p = state.phase;
        uint32_t p_start = state.phase_start_time;
        bool wait_req = state.ped_request;
        xSemaphoreGive(state_mutex);

        Light car_light, ped_light;
        uint32_t elapsed = millis() - p_start;
        int seconds_left = 0;

        switch (p) {
            case CAR_GREEN:  car_light = GREEN;  ped_light = RED;   break;
            case CAR_YELLOW: car_light = YELLOW; ped_light = RED;   break;
            case ALL_RED:    car_light = RED;    ped