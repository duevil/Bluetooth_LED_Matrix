#include "Button.hpp"

Button::state_t Button::read() const {
    state_t returnValue = state_t::RELEASED;
    static uint32_t read = millis();
    static auto wasPressed = false;
    static bool mayReturn = true;
    auto state = !digitalRead(pin);

    if (millis() - read > 200) {
        read = millis();

        if (wasPressed) {
            if (state) {
                returnValue = state_t::PRESSED_CONTINUOUSLY;
            } else {
                returnValue = state_t::PRESSED;
            }
        }

        if (state && !wasPressed) {
            wasPressed = true;
        } else if (!state) {
            wasPressed = false;
        }

        if (mayReturn && returnValue != state_t::RELEASED) {
            mayReturn = false;
            return returnValue;
        }

        if (returnValue == state_t::RELEASED) {
            mayReturn = true;
        }
    }

    return state_t::RELEASED;
}
