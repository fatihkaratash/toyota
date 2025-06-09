Project Role & Position in Overall Architecture
The Filebeat + Docker Compose module provides centralized log aggregation and unified deployment for all microservices in the Toyota Financial Data Platform.

1️⃣ Filebeat captures Docker container logs from all microservices and forwards them to OpenSearch for monitoring and analytics.
2️⃣ Docker Compose allows all services to be launched, configured, and managed as a single system:

css
Kopyala
Düzenle
[ All Microservices (Docker) ] → [ Filebeat ] → [ OpenSearch ]
This setup simulates a real-world enterprise distributed architecture with centralized log monitoring.

🎯 What Filebeat Does
Monitors logs from /var/lib/docker/containers/*/*.log

Enriches logs with Docker metadata

Ships logs into OpenSearch cluster

Runs as a lightweight sidecar container

🎯 What Docker Compose Does
Starts all required services as isolated containers:

diff
Kopyala
Düzenle
- TCP Rate Provider
- REST Rate Provider
- Main Application (Coordinator)
- Kafka + Zookeeper
- Kafka Consumer
- PostgreSQL
- Redis or Hazelcast
- OpenSearch
- Filebeat
Ensures service dependencies and networking

Simplifies development, testing, and production deployment

🗂️ File Structure
arduino
Kopyala
Düzenle
filebeat-config/
├── filebeat.yml → Filebeat configuration
├── Dockerfile → Custom Filebeat Docker image
└── README.md

docker-compose.yml → Root project level
📝 Example filebeat.yml
yaml
Kopyala
Düzenle
filebeat.inputs:
- type: container
  paths:
    - /var/lib/docker/containers/*/*.log
  processors:
    - add_docker_metadata: ~

output.elasticsearch:
  hosts: ["http://opensearch:9200"]
  username: "admin"
  password: "admin"
🚀 How to Build and Run
1️⃣ Build Filebeat Docker Image
arduino
Kopyala
Düzenle
cd filebeat-config/
docker build -t filebeat .
2️⃣ Start Entire System
bash
Kopyala
Düzenle
cd root_project_folder/
docker-compose build
docker-compose up
3️⃣ Verify Logs in OpenSearch
Access OpenSearch dashboard or use OpenSearch REST API

Check for filebeat-* index and logs from all services

📝 docker-compose.yml - Example Services
yaml
Kopyala
Düzenle
services:
  zookeeper:
  kafka:
  tcp-rate-provider:
  rest-rate-provider:
  main-application:
  kafka-consumer:
  postgres:
  opensearch:
  filebeat:
All containers run as part of same default network and can resolve each other by container name.

✅ Features Implemented
Centralized logging for all microservices

Real-time streaming logs into OpenSearch

Docker Compose unified deployment

Scalable, reproducible full-system simulation

Lightweight and minimal resource footprint

🎯 Recommended Tests
telnet tcp-rate-provider 8081 → subscribe|PF1_USDTRY

curl http://rest-rate-provider:8080/api/rates/PF2_USDTRY

docker-compose logs main-application → should show rate updates

Check filebeat-* index in OpenSearch

🚧 Future Improvements (Optional)
Add Kibana for real-time log visualization

Add monitoring & alerting using Prometheus + Grafana

Enable Docker Swarm or Kubernetes compatibility

🔑 Conclusion
The Filebeat + Docker Compose module completes the enterprise-grade deployment of the Toyota Financial Data Platform.
It enables:

Full-system testing

Centralized log management

Seamless microservice orchestration

This setup fully prepares the project for demo, production testing, and scalability testing.

