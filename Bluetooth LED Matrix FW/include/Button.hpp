#ifndef BUTTON_HPP
#define BUTTON_HPP

#include <Arduino.h>


/**
 * @class Button
 * @brief A class that represents a button on an Arduino board.
 *
 * This class provides an interface for interacting with a button on an Arduino board.
 * It provides methods for attaching and detaching interrupts, reading the state of the button, and initializing the button.
 */
class Button {
private:
    uint8_t pin; ///< The pin number on the Arduino board where the button is connected.

public:
    /**
     * @enum state_t
     * @brief An enumeration of the possible states of the button.
     */
    enum class state_t {
        RELEASED, ///< The button is not being pressed.
        PRESSED, ///< The button is being pressed.
        PRESSED_CONTINUOUSLY ///< The button is being pressed continuously.
    };

    /**
     * @brief Construct a new Button object.
     *
     * @param pin The pin number on the Arduino board where the button is connected.
     */
    explicit Button(uint8_t pin) : pin(pin) {}

    /**
     * @brief Initialize the button.
     *
     * This method sets the pin mode to INPUT_PULLUP.
     */
    void begin() const { pinMode(pin, INPUT_PULLUP); }

    /**
     * @brief Attach an interrupt to the button.
     *
     * This method attaches an interrupt to the button that triggers when the button is pressed (FALLING edge).
     *
     * @tparam ISR_F The type of the interrupt service routine function.
     * @param isr The interrupt service routine function.
     */
    template<typename ISR_F>
    void attachInterrupt(ISR_F isr) const { ::attachInterrupt(digitalPinToInterrupt(pin), isr, FALLING); }

    /**
     * @brief Detach the interrupt from the button.
     *
     * This method detaches the interrupt from the button.
     */
    void detachInterrupt() const { ::detachInterrupt(digitalPinToInterrupt(pin)); }

    /**
     * @brief Read the state of the button.
     *
     * This method returns the current state of the button (RELEASED, PRESSED, or PRESSED_CONTINUOUSLY).
     *
     * @return The current state of the button.
     */
    state_t read() const;
};


#endif //BUTTON_HPP
