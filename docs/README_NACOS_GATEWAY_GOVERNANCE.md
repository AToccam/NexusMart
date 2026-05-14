# NexusMart Nacos + Gateway + Traffic Governance Guide

## 1. Start full environment

```bash
docker compose up -d --build
```

Expected key ports:

- Nacos: `8848`
- Gateway: `8080`
- Seckill app: `8081`
- Nginx: `80`

## 2. Publish Nacos configurations

Run from project root:

```bash
./scripts/publish-nacos-config.sh
```

The script publishes:

- `nexusmart-seckill-dev.yml`
- `nexusmart-gateway-dev.yml`

If you use a custom namespace ID:

```bash
NACOS_NAMESPACE=<namespace-id> ./scripts/publish-nacos-config.sh
```

## 3. Verify service registration and routing

Open Nacos console: `http://127.0.0.1:8848/nacos`

- username: `nacos`
- password: `nacos`

Check service list contains:

- `nexusmart-seckill`
- `nexusmart-gateway`

Call through gateway static route:

```bash
curl "http://127.0.0.1:8080/api/seckill/config/current"
```

Call through gateway dynamic discovery route:

```bash
curl "http://127.0.0.1:8080/nexusmart-seckill/api/seckill/config/current"
```

If both return JSON, gateway routing is correct.

## 4. Verify dynamic config refresh

1. In Nacos, edit `nexusmart-seckill-dev.yml` and change:

```yaml
nexusmart:
  dynamic:
    message: "hello-from-nacos-v2"
```

2. Query again:

```bash
curl -X POST "http://127.0.0.1:8081/actuator/refresh"
curl "http://127.0.0.1:8080/api/seckill/config/current"
```

3. Confirm `dynamicMessage` changed without restarting app.

## 5. Traffic governance test (circuit breaker, rate limit, fallback)

### 5.1 Trigger fallback/circuit breaker

Update `nexusmart-seckill-dev.yml` in Nacos:

```yaml
nexusmart:
  pressure:
    simulate-latency-ms: 2000
    failure-rate: 60
```

Then continuously call:

```bash
curl -X POST "http://127.0.0.1:8081/actuator/refresh"
curl -X POST "http://127.0.0.1:8080/actuator/refresh"
curl "http://127.0.0.1:8080/api/seckill/pressure/ping?userId=1"
```

You should observe some fallback responses similar to:

```json
{"code":503,"message":"网关触发服务降级，请稍后重试"}
```

### 5.2 Trigger rate limiting

Use high concurrency load (JMeter below), then watch status codes in report.

Expected symptoms:

- `200`: normal pass-through
- `429`: gateway rate-limited
- `503`: gateway fallback from circuit breaker / timeout

## 6. JMeter stress test

### 6.1 CLI run

```bash
jmeter -n \
  -t jmeter/gateway-governance-test.jmx \
  -Jhost=127.0.0.1 \
  -Jport=8080 \
  -Jthreads=300 \
  -Jloops=30 \
  -Jramp=30 \
  -l jmeter/results/gateway-governance.jtl \
  -e -o jmeter/results/report
```

### 6.2 Analyze results

Open report:

- `jmeter/results/report/index.html`

Focus on:

- Error %
- Throughput
- Response Time Percentiles
- Response code distribution (`200`, `429`, `503`)

## 7. Key endpoints for quick checks

- Gateway health: `http://127.0.0.1:8080/actuator/health`
- Seckill config endpoint (through gateway): `http://127.0.0.1:8080/api/seckill/config/current`
- Seckill pressure endpoint (through gateway): `http://127.0.0.1:8080/api/seckill/pressure/ping?userId=1`
