📋 Project Role & Position in Overall Architecture
The TCP Rate Provider is one of the external market data simulation modules of the Toyota Financial Data Platform.
Its purpose is to simulate real-time streaming of financial rates via TCP protocol for subscribed clients.
The core platform (Main Application) connects to this service to receive raw FX market rates.

This module represents a realistic market data feed in the overall microservice architecture:

[ TCP Provider ] → [ Main Application (Coordinator) ] → [ Kafka ] → [ Consumers ]
This service is designed as a standalone pure Java application (non-Spring) to keep it lightweight, fast, and platform-independent.

🎯 What It Does
Runs a TCP server socket on a configurable port (default = 8081)

Accepts client connections (telnet, netcat, or Main Application subscriber)

Clients can subscribe to FX rates using simple commands

Simulates realistic market rate fluctuations over time

Publishes rates to subscribed clients at configurable intervals

Handles multiple clients concurrently (multi-threaded)

💡 Supported Commands
Command	Description
`subscribe	PAIR_NAME`
`unsubscribe	PAIR_NAME`
Invalid / Unknown command	Responds with `ERROR

Example:

Kopyala
Düzenle
subscribe|PF1_USDTRY
unsubscribe|PF1_USDTRY
🗂️ File Structure
swift
Kopyala
Düzenle
tcp-rate-provider/
├── config/
│   └── initial-rates.json  <-- Initial currency pair data
├── src/main/java/com/toyota/tcpserver/
│   ├── ClientHandler.java
│   ├── ConfigurationReader.java
│   ├── Rate.java
│   ├── RateFluctuationSimulator.java
│   ├── RatePublisher.java
│   ├── TcpServer.java
│   └── TcpRateProviderApplication.java
├── src/main/resources/
│   ├── log4j2.xml            <-- Logging configuration
│   └── tcp-provider.properties <-- Server and fluctuation parameters
├── src/test/java/com/toyota/tcpserver/
├── Dockerfile
├── pom.xml
└── README.md
⚙️ How Rate Fluctuation Works
Initial rates are read from config/initial-rates.json
Each rate has fields: pairName, bid, ask, timestamp
The system randomly fluctuates bid/ask values on every publish cycle
Fluctuation range and interval are configurable in code or file
Example rate:
PF1_USDTRY | 33.60 | 35.90 | 2025-05-01T10:00:00

🚀 How to Run
1️⃣ Run Locally
mvn clean install
 java -jar target\tcp-rate-provider-1.0.0-uber.jar
2️⃣ Run via Docker
docker build -t tcp-provider .
docker run -p 8081:8081 tcp-provider

🔧 Test with Telnet
telnet localhost 8081
subscribe|PF1_USDTRY
unsubscribe|PF1_USDTRY
subscribe|INVALID  → ERROR|Unknown command
or with netcat:
nc localhost 8081
📝 Configuration
Parameter	Location	Description
Server Port	src/main/resources/tcp-provider.properties	Default = 8081
Update Interval	src/main/resources/tcp-provider.properties	Time between broadcasts
Volatility	src/main/resources/tcp-provider.properties	Controls rate simulation variation
Min Spread	src/main/resources/tcp-provider.properties	Minimum spread between bid/ask
Initial Rates	config/initial-rates.json	List of initial rates to load

✅ Features Implemented
Pure multi-threaded TCP Server (non-Spring)

Handles multiple client subscriptions
Real-time rate updates
Exception safety and graceful client disconnection
Minimal memory + CPU footprint
Lightweight Docker container ready

🚧 Future Improvements (Optional)
Externalize more configurations to file
Add Prometheus metrics
Add automatic reconnect from Main Application subscriber

🔑 Conclusion
This module is an essential simulation provider in the full Toyota Financial Market Data Platform.
It emulates an institutional-grade TCP data stream for downstream applications to test subscription, real-time processing, and fault-tolerance logic.
By being pure standalone Java, it guarantees maximum portability, performance, and minimal dependencies.

/
# Değişken adını düzelt
$client = New-Object System.Net.Sockets.TcpClient

try {
    # localhost yerine Docker Compose servis adını veya IP'yi kullanın
    # Eğer bu script'i host makinenizde çalıştırıyorsanız ve tcp-rate-provider
    # container'ı 8081 portunu host'a map'liyorsa (ports: - "8081:8081"),
    # o zaman "localhost" veya "127.0.0.1" doğru olabilir.
    # Eğer bu script başka bir container içinden çalışacaksa, Docker network'ü
    # ve servis adını kullanmanız gerekir.
    Write-Host "Connecting to localhost:8081..."
    $client.Connect("localhost", 8081) # VEYA TCP Provider'ınızın doğru adresi
    Write-Host "Connected!"

    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $reader = New-Object System.IO.StreamReader($stream)

    # Subscribe to a currency pair
    $subscribeCommand = "subscribe|PF1_USDTRY"
    Write-Host "Sending: $subscribeCommand"
    $writer.WriteLine($subscribeCommand)
    $writer.Flush() # Ensure data is sent immediately

    # Read and display incoming data continuously
    Write-Host "Waiting for data..."
    while ($client.Connected) {
        try {
            if ($stream.DataAvailable) {
                $data = $reader.ReadLine()
                if ($data -ne $null) { # Check if ReadLine returned null (stream closed)
                    Write-Host "Received: $data"
                } else {
                    Write-Host "Stream closed by server."
                    break
                }
            }
            # Prevent busy-waiting, give CPU some rest
            # DataAvailable olmadan sürekli ReadLine yapmak bloklayabilir veya CPU kullanabilir.
            # Daha iyi bir yöntem, ReadLine'ın bloklamasına izin vermek ve timeout'u handle etmektir.
            # Ama basit test için bu DataAvailable kontrolü ve sleep bir başlangıç.
            Start-Sleep -Milliseconds 200
        } catch [System.IO.IOException] {
            Write-Host "IOException (Connection likely closed or error): $($_.Exception.Message)"
            break
        } catch {
            Write-Host "An error occurred: $($_.Exception.Message)"
            break
        }
    }
}
catch [System.Net.Sockets.SocketException] {
    Write-Host "SocketException during connect or operation: $($_.Exception.Message)"
}
catch {
    Write-Host "A general error occurred: $($_.Exception.Message)"
}
finally {
    # Cleanup when done or on error
    Write-Host "Cleaning up resources..."
    if ($writer -ne $null) { $writer.Close() }
    if ($reader -ne $null) { $reader.Close() }
    if ($stream -ne $null) { $stream.Close() }
    if ($client -ne $null) { $client.Close() }
    Write-Host "Cleanup complete."
}
*/