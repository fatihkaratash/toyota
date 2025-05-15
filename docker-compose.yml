version: '3.8'

services:
  # Rate Provider Services
  toyota-rate-provider:
    image: toyota/tcp-rate-provider:latest
    build:
      context: ./data-providers/tcp-rate-provider
    ports:
      - "8081:8081"
    networks:
      - toyota-network
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "8081"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 20s
    restart: unless-stopped

  toyota-rest-provider:
    image: toyota/rest-rate-provider:latest
    build:
      context: ./data-providers/rest-rate-provider
    ports:
      - "8082:8081"
    networks:
      - toyota-network
    depends_on:
      - toyota-rate-provider
    restart: unless-stopped

  # Main Coordinator Application
  toyota-coordinator:
    image: toyota/coordinator:latest
    build:
      context: ./main-application
    ports:
      - "8080:8082"
    depends_on:
      - toyota-rate-provider
      - toyota-rest-provider
      - redis
      - kafka
    networks:
      - toyota-network
    restart: unless-stopped

  # Consumer Service
  toyota-consumer:
    image: toyota/consumer:latest
    build:
      context: ./kafka-consumer
    ports:
      - "8084:8084"
    depends_on:
      - kafka
      - postgres
      - opensearch-node1
    networks:
      - toyota-network
    restart: unless-stopped

  # Rate Calculator Service
  toyota-calculator:
    image: toyota/calculator:latest
    build:
      context: ./filebeat
    ports:
      - "8083:8083"
    depends_on:
      - redis
    networks:
      - toyota-network
    restart: unless-stopped

  # Infrastructure Services
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks:
      - toyota-network
    restart: unless-stopped

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks:
      - toyota-network
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - toyota-network
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-password}
      POSTGRES_DB: ${POSTGRES_DB:-toyota_db}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - toyota-network
    restart: unless-stopped

  opensearch-node1:
    image: opensearchproject/opensearch:latest
    environment:
      - cluster.name=opensearch-cluster
      - node.name=opensearch-node1
      - discovery.type=single-node
      - bootstrap.memory_lock=true
      - "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m"
    ulimits:
      memlock:
        soft: -1
        hard: -1
      nofile:
        soft: 65536
        hard: 65536
    ports:
      - "9200:9200"
      - "9600:9600"
    volumes:
      - opensearch-data:/usr/share/opensearch/data
    networks:
      - toyota-network
    restart: unless-stopped

  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:latest
    ports:
      - "5601:5601"
    environment:
      - 'OPENSEARCH_HOSTS=["http://opensearch-node1:9200"]'
    depends_on:
      - opensearch-node1
    networks:
      - toyota-network
    restart: unless-stopped

volumes:
  postgres-data:
  opensearch-data:

networks:
  toyota-network:
    driver: bridge
