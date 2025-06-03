#include <Arduino.h>
#include <ArduinoBLE.h>
#include <HX711.h>


// ===== Pin Configuration =====
const int NTC_PIN = A0;  // Analog pin for NTC
const int HX711_DOUT = 5;
const int HX711_SCK = 4;
const int HEATER_PWM_PIN = 6;  // PWM-capable
const int LED_PIN = LED_BUILTIN;  // Built-in LED for BLE status

// ===== BLE Services and Characteristics =====
BLEService controlService("000102030405060708090a0b0c0d0e0f");  // custom service

BLECharacteristic startStopChar("000102030405060708090a0b0c0d0e10", BLEWrite, 1);
BLECharacteristic tempProfileChar("000102030405060708090a0b0c0d0e11", BLEWrite, 24);         // 6 pairs * 2 uint16 words each
BLECharacteristic feedbackChar("000102030405060708090a0b0c0d0e12", BLERead | BLENotify, 4);  // temperature + force
BLECharacteristic tareChar("000102030405060708090a0b0c0d0e13", BLEWrite, 1); //we might need to tare...
BLECharacteristic thresholdChar("000102030405060708090a0b0c0d0e14", BLEWrite, 4); //setting the threshold...


// ===== Force Sensor =====
HX711 scale;

// ===== Profile Data =====
float profileTime[6] = { 1,2,3,4,5,6 };
float profileTemp[6] = { 25,150,210,230,180,25 };

bool running = false;
bool profileStarted = false;
unsigned long profileStartTime = 0;
float forceThreshold = 200000.0;  // adjust per sensor

// ===== NTC Constants =====
const float SERIES_RESISTOR = 1996.0;
const float NOMINAL_RESISTANCE = 100000.0;
const float NOMINAL_TEMPERATURE = 25.0;
const float B_COEFFICIENT = 3950.0;

// ===== 2-Point Controller =====
float currentTarget = 25.0;
float hysteresis = 0.5;

// ===== BLE State Tracking =====
bool bleConnected = false;
bool lastConnectionStatus = false;

// ===== Utilities =====
float readNTCTemperature() {
  int adc = analogRead(NTC_PIN);
  Serial.print(adc);
  Serial.print(", ");
  
  float resistance = SERIES_RESISTOR * ((980.0 / (adc-5)) - 1.0); //adjust per temp sensor
  float steinhart;
  steinhart = resistance / NOMINAL_RESISTANCE;
  steinhart = log(steinhart);
  steinhart /= B_COEFFICIENT;
  steinhart += 1.0 / (NOMINAL_TEMPERATURE + 273.15);
  steinhart = 1.0 / steinhart;
  return steinhart - 273.15;
}

float getTargetTemp(unsigned long elapsed) {
  float tSec = elapsed / 1000.0;
  for (int i = 0; i < 5; i++) {
    if (tSec < profileTime[i + 1]) {
      float dt = profileTime[i + 1] - profileTime[i];
      float tempDiff = profileTemp[i + 1] - profileTemp[i];
      Serial.print(i);
      Serial.print(", ");
      Serial.print(tSec);
      Serial.print(", ");
      Serial.print(profileTime[i + 1]);
      Serial.print(", ");
      Serial.print(profileTemp[i + 1]);
/*      Serial.print(", ");
      Serial.print(dt);
      Serial.print(", ");
      Serial.print(tempDiff);*/
      Serial.print(", ");

      return profileTemp[i] + (tSec - profileTime[i]) * tempDiff / dt;
    }
  }
  profileStarted = false; //finished profile
  return profileTemp[5];
}

void controlHeater(float currentTemp) {
  float onThreshold = currentTarget - hysteresis / 2;
  float offThreshold = currentTarget + hysteresis / 2;
  static bool heaterOn = false;

  if (currentTemp < onThreshold && !heaterOn) {
    analogWrite(HEATER_PWM_PIN, 255);  // full power
    heaterOn = true;
  } else if (currentTemp > offThreshold && heaterOn) {
    analogWrite(HEATER_PWM_PIN, 0);
    heaterOn = false;
  }
  Serial.print(heaterOn?"1, ":"0, ");
}

void processBLE() {
  if (startStopChar.written()) {
    running = startStopChar.value()[0];
    if (!running) {
      analogWrite(HEATER_PWM_PIN, 0);
      profileStarted = false;
    }
  }

  if (tempProfileChar.written()) {
    Serial.println("Written");
    const uint8_t* data = tempProfileChar.value();

    for (int i = 0; i < 6; i++) {
      profileTime[i] = data[i * 4]*256 + data[i * 4 + 1];             // in seconds, little endian
      profileTemp[i] = (data[i * 4 + 2]*256 + data[i * 4 + 3]) / 10.0;  // °C
      Serial.print( profileTime[i]);
      Serial.println(profileTemp[i]);

    }
  }

  if (tareChar.written()){
    Serial.println("Tare Written");
    scale.tare();
  }

  if (thresholdChar.written()){
    Serial.print("thresholdChar Written : ");
    const uint8_t* data = thresholdChar.value();
    
    forceThreshold = (data[3] << 24) + (data[2] << 16) + (data[1] << 8) + data[0];
    Serial.println(forceThreshold);
   
  }

}

void sendFeedback(float temp, float force) {
  uint16_t t = (uint16_t)(temp * 10);
  uint16_t f = (uint16_t)(force);
  uint8_t buffer[4] = { (uint8_t)(t >> 8), (uint8_t)(t & 0xFF), (uint8_t)(f >> 8), (uint8_t)(f & 0xFF) };
  feedbackChar.writeValue(buffer, 4);
}

void setup() {
  Serial.begin(115200);
  analogReadResolution(10);
  pinMode(HEATER_PWM_PIN, OUTPUT);
  analogWrite(HEATER_PWM_PIN, 0);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // Init HX711
  scale.begin(HX711_DOUT, HX711_SCK);
  scale.set_scale();  // You can calibrate this
  scale.tare();

  // Init BLE
  if (!BLE.begin()) {
    Serial.println("BLE init failed!");
    while (1);
  }

  controlService.addCharacteristic(startStopChar);
  controlService.addCharacteristic(tempProfileChar);
  controlService.addCharacteristic(feedbackChar);
  controlService.addCharacteristic(tareChar);
  controlService.addCharacteristic(thresholdChar);

  BLE.setLocalName("Martins-NTC-BLE-Controller");
  BLE.setAdvertisedService(controlService);
  BLE.addService(controlService);
  BLE.advertise();

  Serial.println("BLE device is ready!");
}

void loop() {

  unsigned long elapsed;
  BLE.poll();

  BLEDevice central = BLE.central();
  bleConnected = central;

  if (bleConnected != lastConnectionStatus) {
    digitalWrite(LED_PIN, bleConnected ? HIGH : LOW);
    Serial.println(bleConnected ? "BLE Connected" : "BLE Disconnected");
    lastConnectionStatus = bleConnected;
  }

  processBLE();

  float temp = readNTCTemperature();
  float force = scale.get_units(1);
  sendFeedback(temp, force);

  if (running) {
    if (!profileStarted && force > forceThreshold) {
      profileStartTime = millis();
      profileStarted = true;
    }

    if (profileStarted) {
      elapsed = millis() - profileStartTime;
      currentTarget = getTargetTemp(elapsed);
      controlHeater(temp);
    }
  }

  Serial.print(profileStarted);
  Serial.print(", ");
  Serial.print(running);
  Serial.print(", ");
  Serial.print(currentTarget);
  Serial.print("; ");
 /* for (int i = 0; i < 6; i++) {
    Serial.print(profileTime[i]);
    Serial.print(" ");
    Serial.print(profileTemp[i]);
    Serial.print("; ");
  }
  Serial.print(elapsed);
  Serial.print(", ");*/
  Serial.print(temp);
  Serial.print(", ");
  Serial.print(force);
  Serial.println(" ");

  delay(200);
}
