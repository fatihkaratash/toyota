spring:
  application:
    name: main-application
  data:
    redis:
      host: redis  # Docker container name
      port: 6379
      database: 0
      timeout: 5000ms
  kafka:
    bootstrap-servers: kafka:9092  # ✅ External port (Docker exposed)
    producer:
      acks: all
      retries: 3
      batch-size: 16384
      buffer-memory: 33554432
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8082

logging:
  config: classpath:log4j2.xml
  level:
    root: INFO
    org.springframework: WARN
    com.toyota: DEBUG
    com.toyota.tcpserver: INFO
    org.springframework.web.client: DEBUG
    org.springframework.http: DEBUG
    org.springframework.web.reactive.function.client: DEBUG
    reactor.netty.http.client: DEBUG

subscribers:
  config:
    path: "classpath:subscribers.json"

app:
  calculator:
    config:
      path: "classpath:calculation-config.json"
  symbol:
    format: "STANDARD"  # 6-char uppercase format
  aggregator:
    window-time-ms: 5000
    max-time-skew-ms: 2000
  providers:
    simple-topic-enabled: "PF1,PF2,REST2,TCP1"
  kafka:
    topics:
      raw-rates: "raw-rates"
      calculated-rates: "calculated-rates"
      simple-rates: "simple-rates"
    topic:
      raw-rates: "financial-raw-rates"
      calculated-rates: "financial-calculated-rates"
      simple-rates: "financial-simple-rates"
      partitions: 3
      replication: 1
    throttle-interval-ms: 1000
  provider:
    tcp:
      default-port: 8081
      default-host: "tcp-rate-provider"  # Docker container name
    rest:
      default-port: 8080
      default-host: "rest-rate-provider"  # Docker container name
  cache:
    raw-rate:
      ttl-seconds: 300
    calculated-rate:
      ttl-seconds: 300
  subscriber:
    threadpool:
      coreSize: 5
      maxSize: 10
      queueCapacity: 25
  calculation:
    threadpool:
      coreSize: 2
      maxSize: 5
      queueCapacity: 10

# Provider Authentication Configuration
providers:
  rest:
    url: "${REST_PROVIDER_URL:http://rest-rate-provider:8080}"  # Docker container name
    username: "${CLIENT_REST_USERNAME:rest_kullanici_adi}"
    password: "${CLIENT_REST_PASSWORD:rest_sifre}"
  tcp:
    host: "${TCP_PROVIDER_HOST:tcp-rate-provider}"  # Docker container name
    port: "${TCP_PROVIDER_PORT:8081}"
    username: "${CLIENT_TCP_USERNAME:tcp_kullanici_adi}"
    password: "${CLIENT_TCP_PASSWORD:tcp_sifre}"

# Resilience4j Configuration
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable
  circuitbreaker:
    configs:
      default:
        failureRateThreshold: 50
        slowCallRateThreshold: 100
        slowCallDurationThreshold: 1s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 30s
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.reactive.function.client.WebClientRequestException