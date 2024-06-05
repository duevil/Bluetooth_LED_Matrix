#ifndef UART_SERIAL_H
#define UART_SERIAL_H

#include <Arduino.h>

 #define USE_SERIAL

#ifdef USE_SERIAL
#define uart_begin(baud) Serial.begin(baud)
#define uart_print(...) Serial.print(__VA_ARGS__)
#define uart_println(...) Serial.println(__VA_ARGS__)
#define uart_flush() Serial.flush()
#else
#define uart_begin(baud) (void)__LINE__
#define uart_print(...) (void)__LINE__
#define uart_println(...) (void)__LINE__
#define uart_flush() (void)__LINE__
#endif

#endif //UART_SERIAL_H
