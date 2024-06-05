#include <Arduino.h>
#include "Adafruit_NeoPixel.h"

#ifndef COLOR_H
#define COLOR_H

/**
 * @struct color_t
 * @brief A structure that represents a color in RGB format.
 *
 * This structure provides an interface for interacting with a color in RGB format.
 * It provides methods for comparing colors, converting the color to a uint32_t, fading to another color, and setting the color to a random value.
 */
struct color_t {
    uint8_t r = 0; ///< The red component of the color.
    uint8_t g = 0; ///< The green component of the color.
    uint8_t b = 0; ///< The blue component of the color.

    /**
     * @brief Compare this color with another color.
     *
     * @param c The other color.
     * @return True if the two colors are the same, false otherwise.
     */
    bool operator==(const color_t &c) const { return r == c.r && g == c.g && b == c.b; }

    /**
     * @brief Convert the color to a uint32_t.
     *
     * @return The color as a uint32_t.
     */
    explicit operator uint32_t() const { return Adafruit_NeoPixel::Color(r, g, b); }

    /**
     * @brief Fade this color to another color.
     *
     * @param c The other color.
     */
    void fadeTo(const color_t &c) {
        if (r != c.r) r = (r < c.r) ? (r + 1) : (r - 1);
        if (g != c.g) g = (g < c.g) ? (g + 1) : (g - 1);
        if (b != c.b) b = (b < c.b) ? (b + 1) : (b - 1);
    }

    /**
     * @brief Set the color to a random value.
     */
    void setRandom() {
        auto color = random(3);
        r = getRnd(color == 0);
        g = getRnd(color == 1);
        b = getRnd(color == 2);
    }

private:
    /**
     * @brief Get a random value for a color component.
     *
     * @param high If true, the random value will be in the range [0, 256), otherwise it will be in the range [0, 8).
     * @return The random value.
     */
    static uint8_t getRnd(bool high) { return (uint8_t) random(high ? 256 : 8); }
};

#endif
