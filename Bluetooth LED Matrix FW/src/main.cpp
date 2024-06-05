#include <Arduino.h>
#include <SoftwareSerial.h>
#include <Adafruit_NeoPixel.h>
#include <avr/sleep.h>
#include "uart_serial.h"
#include "Button.hpp"
#include "color.h"


/*
 * Bluetooth command structure:
 *
 * 0x01
 *      get the color of all leds
 *      1 byte: cmd
 *      respond: cmd, status, ([number, r, g, b] * count)
 * 0x02
 *      set some specific leds to a specific color
 *      at most count * 4 + 1 bytes: cmd, [number, r, g, b] * count
 *      respond: cmd, status
 * 0x03
 *      set all leds to a specific color
 *      4 bytes: cmd, r, g, b
 *      respond: cmd, status
 *
 * respond codes:
 *      0x00: success
 *      0x01: invalid data length
 *      0x02: led number out of range
 *      0xFE: invalid state
 *      0xFF: invalid command
 */


constexpr auto BLUETOOTH_BAUD_RATE = 38400;
constexpr auto BLUETOOTH_RX_PIN = 3;
constexpr auto BLUETOOTH_TX_PIN = 4;
constexpr auto LED_COUNT = 64;
constexpr auto LEDS_DATA_PIN = 11;
constexpr auto BUTTON_PIN = 2;
constexpr auto DELAY = 50;

enum class cmd_t {
    NONE = 0x00,
    GET_LEDS = 0x01,
    SET_LEDS = 0x02,
    SET_LEDS_ALL = 0x03,
};

enum class state_t {
    OK = 0x00,
    INVALID_DATA_LENGTH = 0x01,
    LED_OUT_OF_RANGE = 0x02,
    INVALID_STATE = 0xFE,
    INVALID_COMMAND = 0xFF,
};

bool operator==(int data, cmd_t cmd) { return static_cast<int>(cmd) == data; }

enum class mode_t {
    OFF, RANDOM, BT,
};


SoftwareSerial btSer(BLUETOOTH_TX_PIN, BLUETOOTH_RX_PIN);
Adafruit_NeoPixel leds(LED_COUNT, LEDS_DATA_PIN, NEO_GRB + NEO_KHZ800);
Button button(BUTTON_PIN);
volatile mode_t mode = mode_t::RANDOM;


void btRespond(cmd_t cmd, state_t state, const uint8_t *data, size_t length);
void randomColors();


void cmdNone(int16_t &count, state_t &state, cmd_t &cmd, uint8_t data);
void cmdGetLeds(int16_t count, state_t &state, uint8_t *ledData, uint8_t data);
void cmdSetLeds(int16_t count, state_t &state, uint8_t *ledData, uint8_t data);
void cmdSetLedsAll(int16_t count, state_t &state, uint8_t *ledData, uint8_t data);


/**
 * @brief Setup
 * - Starts the UART communication with a baud rate of 115200.
 * - Waits for 1000 milliseconds for the Bluetooth module to start up.
 * - Starts the Bluetooth serial communication with a baud rate of 38400.
 * - Initializes the LED strip.
 * - Initializes the button.
 * - Prints "BOOT FINISHED" to the UART.
 */
void setup() {
    uart_begin(115200);
    delay(1000); // wait for the bluetooth module to start up
    btSer.begin(BLUETOOTH_BAUD_RATE);
    leds.begin();
    button.begin();
    uart_println("BOOT FINISHED");
}

/**
 * @brief Loop
 * It handles button press events, sets the mode of operation, and handles Bluetooth serial communication for receiving commands and sending responses.
 * The button press events are handled in the following way:
 * - If the button is pressed, the mode is set to RANDOM.
 * - If the button is pressed continuously, the mode is set to OFF.
 * - If the button is released, no action is taken.
 * The mode of operation is handled in the following way:
 * - If the mode is OFF, the device goes to sleep and wakes up when the button is pressed.
 * - If the mode is RANDOM, random colors are generated for the LEDs.
 * - If the mode is BT, no action is taken.
 * The Bluetooth serial communication is handled in the following way:
 * - The function reads the command and data from the Bluetooth serial connection.
 * - It then calls the appropriate function to handle the command.
 * - If the state after handling the command is OK, it sends a response over the Bluetooth serial connection.
 * - If the state is not OK, it sends an error response.
 */
void loop() {
    switch (button.read()) {
        case Button::state_t::PRESSED: {
            uart_println("BUTTON PRESSED");
            mode = mode_t::RANDOM;
            break;
        }
        case Button::state_t::PRESSED_CONTINUOUSLY: {
            uart_println("BUTTON PRESSED CONTINUOUSLY");
            mode = mode_t::OFF;
            break;
        }
        case Button::state_t::RELEASED:
            break;
    }

    switch (mode) {
        case mode_t::OFF: {
            button.attachInterrupt([] { mode = mode_t::RANDOM; });
            leds.clear();
            leds.show();
            uart_println("SLEEPING ...");
            uart_flush();
            set_sleep_mode(SLEEP_MODE_PWR_DOWN);
            sleep_enable();
            sleep_bod_disable();
            sleep_cpu();
            button.detachInterrupt();
            uart_println("WAKING UP");
            mode = mode_t::RANDOM;
            break;
        }
        case mode_t::RANDOM: {
            randomColors();
            break;
        }
        case mode_t::BT: {
            break;
        }
    }


    if (!btSer.available()) return;

    int16_t count = -1; // -1 = cmd not received, 0 = cmd received, >0 = data index
    cmd_t cmd = cmd_t::NONE;
    state_t state = state_t::INVALID_DATA_LENGTH;
    uint8_t ledData[LED_COUNT * 4]; // 4 bytes per led (number, r, g, b)

    while (btSer.available()) {
        auto data = btSer.read();
        if (data < 0) {
            uart_println("ERROR: INVALID DATA");
            return;
        } else {
            uart_print("RECEIVED: ");
            uart_println(data, HEX);
        }
        switch (cmd) {
            case cmd_t::NONE:
                uart_println("INFO: CMD NOT RECEIVED");
                cmdNone(count, state, cmd, (uint8_t) data);
                if (cmd != cmd_t::GET_LEDS) break;
                else {
                    count = 0;
                    [[fallthrough]]; // fall through if cmd is GET_LEDS
                }
            case cmd_t::GET_LEDS:
                uart_println("INFO: CMD GET_LEDS");
                cmdGetLeds(count, state, ledData, (uint8_t) data);
                break;
            case cmd_t::SET_LEDS:
                uart_println("INFO: CMD SET_LEDS");
                cmdSetLeds(count, state, ledData, (uint8_t) data);
                break;
            case cmd_t::SET_LEDS_ALL:
                uart_println("INFO: CMD SET_LEDS_ALL");
                cmdSetLedsAll(count, state, ledData, (uint8_t) data);
                break;
        }
        count++;
    }

    uart_print("READ ");
    uart_print(count);
    uart_println(" BYTES");

    if (state == state_t::OK) {
        switch (cmd) {
            case cmd_t::NONE:
                uart_println("should not happen");
                break;
            case cmd_t::GET_LEDS:
                btRespond(cmd, state, ledData, LED_COUNT * 4);
                break;
            case cmd_t::SET_LEDS:
            case cmd_t::SET_LEDS_ALL:
                btRespond(cmd, state, nullptr, 0);
                break;
        }
    } else {
        btRespond(cmd, state, nullptr, 0);
    }
}


/**
 * @brief This function generates random colors for each LED in the LED array.
 *
 * The function maintains two static arrays, `current` and `target`, each of the size `LED_COUNT`.
 * The `current` array represents the current color of each LED, and the `target` array represents the target color that each LED is fading to.
 * The function also maintains a static `delay` variable to control the rate at which the color change occurs.
 *
 * The function works as follows:
 * 1. If the delay has not yet passed, the function returns immediately.
 * 2. For each LED, if its current color is the same as its target color, a new random target color is generated.
 * 3. Each LED's current color is then faded towards its target color.
 * 4. The color of each LED in the LED strip is updated to its new current color.
 * 5. Finally, the updated colors are displayed on the LED strip.
 */
void randomColors() {
    static color_t current[LED_COUNT]; // Current color of each LED
    static color_t target[LED_COUNT]; // Target color of each LED
    static uint32_t delay = 0; // Delay to control the rate of color change

    // If the delay has not yet passed, return immediately
    if (!(delay == 0 || millis() - delay > DELAY)) return;
    delay = millis();

    for (uint8_t i = 0; i < LED_COUNT; i++) {
        // If the current color is the same as the target color, generate a new random target color
        if (current[i] == target[i]) target[i].setRandom();
        // Fade the current color towards the target color
        current[i].fadeTo(target[i]);
        // Update the color of the LED in the LED strip
        leds.setPixelColor(i, current[i].r, current[i].g, current[i].b);
    }
    // Display the updated colors on the LED strip
    leds.show();
}


/**
 * @brief This function sends a response over the Bluetooth serial connection.
 *
 * The function takes a command, a state, a data array, and the length of the data array as parameters.
 * It first writes the command and the state to the Bluetooth serial connection.
 * If the data array is not null, it writes the data array to the Bluetooth serial connection.
 * It then prints a response message to the UART, followed by the state message.
 * If the state is OK, it prints "[SUCCESS]". If the state is INVALID_DATA_LENGTH, it prints "[INVALID DATA LENGTH]".
 * If the state is LED_OUT_OF_RANGE, it prints "[LED OUT OF RANGE]". If the state is INVALID_STATE, it prints "[INVALID STATE]".
 * If the state is INVALID_COMMAND, it prints "[INVALID COMMAND]". For any other state, it prints "[UNKNOWN ERROR]".
 * Finally, it prints the data array to the UART in hexadecimal format.
 *
 * @param cmd The command to be sent.
 * @param state The state of the command execution.
 * @param data The data to be sent. Can be null.
 * @param length The length of the data array.
 */
void btRespond(cmd_t cmd, state_t state, const uint8_t *data, size_t length) {
    btSer.write(static_cast<uint8_t>(cmd));
    btSer.write(static_cast<uint8_t>(state));
    if (data) btSer.write(data, length);
    uart_print("RESPONSE:");
    switch (state) {
        case state_t::OK:
            uart_print(" [SUCCESS]");
            break;
        case state_t::INVALID_DATA_LENGTH:
            uart_print(" [INVALID DATA LENGTH]");
            break;
        case state_t::LED_OUT_OF_RANGE:
            uart_print(" [LED OUT OF RANGE]");
            break;
        case state_t::INVALID_STATE:
            uart_print(" [INVALID STATE]");
            break;
        case state_t::INVALID_COMMAND:
            uart_print(" [INVALID COMMAND]");
            break;
        default:
            uart_print(" [UNKNOWN ERROR]");
            break;
    }
    for (size_t i = 0; i < length; i++) {
        uart_print(' ');
        uart_print(data[i], HEX);
    }
    uart_println();
}


/**
 * @brief This function handles the case when no command has been received yet.
 *
 * The function takes a reference to a state variable, a reference to a command variable, and a data byte as parameters.
 * If the data byte matches any of the valid commands (GET_LEDS, SET_LEDS, SET_LEDS_ALL), the function sets the command variable to the received command.
 * If the data byte does not match any of the valid commands, the function sets the state variable to INVALID_COMMAND.
 *
 * @param state The state of the command execution. This is a reference parameter and the function may modify its value.
 * @param cmd The command to be processed. This is a reference parameter and the function may modify its value.
 * @param data The data byte received. This should be a command code.
 */
void cmdNone(int16_t &, state_t &state, cmd_t &cmd, uint8_t data) {
    if (data == cmd_t::GET_LEDS || data == cmd_t::SET_LEDS || data == cmd_t::SET_LEDS_ALL) {
        cmd = static_cast<cmd_t>(data);
    } else {
        state = state_t::INVALID_COMMAND;
    }
}

/**
 * @brief This function handles the GET_LEDS command.
 *
 * The function takes a count of received bytes, a reference to a state variable, a data array, and a data byte as parameters.
 * If the count of received bytes is less than 0, the function sets the state variable to INVALID_STATE and returns.
 * If the count of received bytes is 0 and the state is either INVALID_DATA_LENGTH or OK, the function retrieves the color of each LED and stores it in the data array.
 * The color is stored as four bytes: the LED number, and the red, green, and blue components of the color.
 * The function then sets the state variable to OK.
 * If the count of received bytes is not 0, the function prints a message indicating that it is consuming extra data.
 *
 * @param count The count of received bytes. This should be 0 when the function is called.
 * @param state The state of the command execution. This is a reference parameter and the function may modify its value.
 * @param ledData The data array where the LED colors will be stored. This should be a pointer to an array of size LED_COUNT * 4.
 * @param data The data byte received. This should be the first byte of the data following the GET_LEDS command.
 */
void cmdGetLeds(int16_t count, state_t &state, uint8_t *ledData, uint8_t data) {
    if (count < 0) {
        state = state_t::INVALID_STATE;
        return;
    }
    if (count == 0 && (state == state_t::INVALID_DATA_LENGTH || state == state_t::OK)) {
        for (uint8_t i = 0; i < LED_COUNT; i++) {
            auto color = leds.getPixelColor(i);
            ledData[i * 4] = i;
            ledData[i * 4 + 1] = (uint8_t) (color >> 16);
            ledData[i * 4 + 2] = (uint8_t) (color >> 8);
            ledData[i * 4 + 3] = (uint8_t) color;
        }
        state = state_t::OK;
    } else {
        uart_print("INFO: CONSUMING EXTRA DATA: ");
        uart_println(data, HEX);
    }
}

/**
 * @brief This function handles the SET_LEDS command.
 *
 * The function takes a count of received bytes, a reference to a state variable, a data array, and a data byte as parameters.
 * If the count of received bytes is less than 0, the function sets the state variable to INVALID_STATE and returns.
 * If the count of received bytes is less than LED_COUNT * 4 and the state is either INVALID_DATA_LENGTH or OK, the function stores the data byte in the data array.
 * If the count of received bytes is a multiple of 4, the function retrieves the LED number and the red, green, and blue components of the color from the data array.
 * If the LED number is out of range, the function sets the state variable to LED_OUT_OF_RANGE and returns.
 * Otherwise, the function sets the color of the specified LED, updates the LED strip, sets the mode to BT, and sets the state variable to OK.
 * If the count of received bytes is not a multiple of 4 or is greater than or equal to LED_COUNT * 4, the function prints a message indicating that it is consuming extra data.
 *
 * @param count The count of received bytes. This should be less than LED_COUNT * 4 when the function is called.
 * @param state The state of the command execution. This is a reference parameter and the function may modify its value.
 * @param ledData The data array where the LED colors will be stored. This should be a pointer to an array of size LED_COUNT * 4.
 * @param data The data byte received. This should be one of the bytes of the data following the SET_LEDS command.
 */
void cmdSetLeds(int16_t count, state_t &state, uint8_t *ledData, uint8_t data) {
    if (count < 0) {
        state = state_t::INVALID_STATE;
        return;
    }
    if (count < LED_COUNT * 4 && (state == state_t::INVALID_DATA_LENGTH || state == state_t::OK)) {
        ledData[count] = data;
        if (count % 4 == 3) {
            auto i = count / 4;
            auto n = ledData[i * 4];
            auto r = ledData[i * 4 + 1];
            auto g = ledData[i * 4 + 2];
            auto b = ledData[i * 4 + 3];
            if (i >= LED_COUNT) {
                state = state_t::LED_OUT_OF_RANGE;
                return;
            }
            leds.setPixelColor(n, r, g, b);
            leds.show();
            mode = mode_t::BT;
            state = state_t::OK;
        }
    } else {
        uart_print("INFO: CONSUMING EXTRA DATA: ");
        uart_println(data, HEX);
    }
}

/**
 * @brief This function handles the SET_LEDS_ALL command.
 *
 * The function takes a count of received bytes, a reference to a state variable, a data array, and a data byte as parameters.
 * If the count of received bytes is less than 0, the function sets the state variable to INVALID_STATE and returns.
 * If the count of received bytes is less than 3 and the state is either INVALID_DATA_LENGTH or OK, the function stores the data byte in the data array.
 * The function then retrieves the red, green, and blue components of the color from the data array.
 * It sets the color of all LEDs to the specified color, updates the LED strip, sets the mode to BT, and sets the state variable to OK.
 * If the count of received bytes is not less than 3, the function prints a message indicating that it is consuming extra data.
 *
 * @param count The count of received bytes. This should be less than 3 when the function is called.
 * @param state The state of the command execution. This is a reference parameter and the function may modify its value.
 * @param ledData The data array where the LED colors will be stored. This should be a pointer to an array of size 3.
 * @param data The data byte received. This should be one of the bytes of the data following the SET_LEDS_ALL command.
 */
void cmdSetLedsAll(int16_t count, state_t &state, uint8_t *ledData, uint8_t data) {
    if (count < 0) {
        state = state_t::INVALID_STATE;
        return;
    }
    if (count < 3 && (state == state_t::INVALID_DATA_LENGTH || state == state_t::OK)) {
        ledData[count] = data;
        auto r = ledData[0];
        auto g = ledData[1];
        auto b = ledData[2];
        leds.fill(Adafruit_NeoPixel::Color(r, g, b));
        leds.show();
        mode = mode_t::BT;
        state = state_t::OK;
    } else {
        uart_print("INFO: CONSUMING EXTRA DATA: ");
        uart_println(data, HEX);
    }
}
