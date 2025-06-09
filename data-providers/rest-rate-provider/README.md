REST Rate Provider - Toyota Finance Project
📋 Project Role & Position in Overall Architecture
The REST Rate Provider is one of the external market data simulation modules of the Toyota Financial Data Platform.
It provides real-time foreign exchange (FX) rates via a simple REST API for on-demand consumption by downstream services, including the Main Application.

This module represents a realistic market data REST feed in the overall microservice architecture:

[ REST Provider ] → [ Main Application (Coordinator) ] → [ Kafka ] → [ Consumers ]
This service is built as a lightweight Spring Boot microservice with simple configuration and minimal dependencies.

🎯 What It Does
Exposes a REST endpoint:

GET /api/rates/{rateName}
Simulates real-world rate fluctuation every time an FX rate is requested

Reads initial FX rates from a configuration file (initial-rates.json)

Throws meaningful errors when an unknown rate is requested

Easily dockerized and designed to run in microservice environments

💡 Sample Request and Response
Request:

GET http://localhost:8080/api/rates/PF2_USDTRY
Response:


{
    "rateName": "PF2_USDTRY",
    "bid": 34.4456,
    "ask": 35.4372,
    "timestamp": "2025-05-01T10:15:00"
}
Error Request Example:

GET http://localhost:8080/api/rates/INVALIDPAIR
Response → 404 Not Found + Error message
🗂️ File Structure

rest-rate-provider/
├── src/main/java/com/toyota/restserver/
├── src/test/java/com/toyota/restserver/
├── src/main/resources/initial-rates.json
├── Dockerfile
├── pom.xml
└── README.md
⚙️ How Rate Fluctuation Works
Rates are initially loaded from initial-rates.json

On every request, bid/ask values fluctuate randomly to simulate real market movement

Fluctuation parameters can be adjusted by configuration

Example rate:

PF2_USDTRY | 34.4456 | 35.4372 | 2025-05-01T10:15:00
🚀 How to Run
1️⃣ Run Locally
mvn clean install
java -jar target/rest-rate-provider.jar
2️⃣ Run via Docker
docker build -t rest-provider .
docker run -p 8080:8080 rest-provider
🔧 Test with curl or Postman
curl http://localhost:8080/api/rates/PF2_USDTRY
or test with Postman:

GET → http://localhost:8080/api/rates/PF2_USDTRY
📝 Configuration
Parameter	Location	Description
Server Port	application.properties	Default = 8080
Initial Rates	initial-rates.json	List of FX rate pairs
Max Fluctuation Range	application.properties	Controls rate simulation variation

✅ Features Implemented
Pure Spring Boot REST API

Reads FX rates from JSON file

Real-time random fluctuation simulation

Exception safety & meaningful error handling

Lightweight Docker image ready for production

Unit tests and integration tests

🚧 Future Improvements (Optional)
Add Prometheus metrics export

Integrate with Spring actuator health check

Externalize fluctuation parameters fully to configuration file

🔑 Conclusion
This module is a critical external market data provider within the Toyota Financial Market Data Platform.
It provides an institutional-grade simulated REST feed to test request-response logic, fault tolerance, and subscriber behavior of downstream services.

Its microservice design guarantees maximum flexibility and production readiness in a distributed system environment.

