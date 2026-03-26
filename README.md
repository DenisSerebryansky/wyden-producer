# Producer API

Swagger is available at http://localhost:8010/swagger-ui/index.html

# RabbitMQ Setup

```bash
docker run \
  -d \
  --hostname my-rabbit \
  --name some-rabbit \
  -p 15672:15672 \
  -p 5672:5672 \
  rabbitmq:3-management
```

## AMQP contract

Exchanges used:

- work-inbound (topic, created by worker)
- work-outbound (topic, created by producer)
- certified-result (topic, created by audit)

Routing keys used:
- task.produced.<color|any> (listened by worker and audit)
- task.processed.<color|any> (listened by producer and audit)
- task.discarded.<color|any> (listened by audit)
- task.certified.<color|any> (listened by audit)

# Scenarios Results

## Basic scenario

1. Start 1 instance of A, B, C each.
2. Start producing on A.
3. Stop producing on A after 30s

#### Reproducing

1. Launch producer, worker and audit from IDE
2. Send start and stop commands to the producer
```bash
curl -X POST http://localhost:8010/start && \
sleep 30 && \
curl -X POST http://localhost:8010/stop && \
echo "Done"
```

3. Check results by calling audit API
```bash
curl -X GET http://localhost:8030/count
````

#### Results

| Metric           | Count |
|------------------|------:|
| Produced tasks   |    15 |
| Processed tasks  |    15 |
| Certified tasks  |    15 |
| Discarded tasks  |     0 |

## Scenario 2

_What are the results when you change the deployment setup to: 3 instances of A, 1 instance of B, 1 instance of C_

#### Reproducing

1. Launch worker and audit from IDE
2. Kill all producers
```bash
pkill -f producer-0.0.1-SNAPSHOT.jar
```
3. Up 3 producers:
```bash
for p in 8010 8011 8012; do java -jar build/libs/producer-0.0.1-SNAPSHOT.jar --server.port=$p > producer-$p.log 2>&1 & done
```

4. Send start and stop commands to the producers
```bash
sleep 5
for p in 8010 8011 8012; do
  curl -X POST http://localhost:$p/start
done
sleep 30
for p in 8010 8011 8012; do
  curl -X POST http://localhost:$p/stop
done
echo "Done"
```

5. Check results by calling audit API
```bash
curl -X GET http://localhost:8030/count
````

#### Results

| Metric           | Count |
|------------------|------:|
| Produced tasks   |    45 |
| Processed tasks  |    30 |
| Certified tasks  |    30 |
| Discarded tasks  |    15 |

## Scenario 3

_What are the results when you change the deployment setup to: 3
instances of A, 3 instance of B, 1 instance of C
AND when the processing time on B is changed to 8 seconds?_

#### Reproducing

1. Launch audit from IDE
2. Reproduce scenario 3 for worker (see its README.md)
3. Kill all producers
```bash
pkill -f producer-0.0.1-SNAPSHOT.jar
```

4. Up 3 producers:
```bash
for p in 8010 8011 8012; do java -jar build/libs/producer-0.0.1-SNAPSHOT.jar --server.port=$p > producer-$p.log 2>&1 & done
```

5. Send start and stop commands to the producers
```bash
sleep 5
for p in 8010 8011 8012; do
  curl -X POST http://localhost:$p/start
done
sleep 30
for p in 8010 8011 8012; do
  curl -X POST http://localhost:$p/stop
done
echo "Done"
```

6. Check results by calling audit API
```bash
curl -X GET http://localhost:8030/count
````

#### Results

| Metric           | Count |
|------------------|------:|
| Produced tasks   |    45 |
| Processed tasks  |     3 |
| Certified tasks  |     3 |
| Discarded tasks  |    42 |

## Scenario 4

_1. Create modified version of B that immediately throws and error on
every second task it receives._

_3. Ensure that all of the tasks are processed by Workers, including those
that initially failed (you can increase the number of deployed Workers
if needed)_

#### Reproducing

1. Launch audit from IDE
2. Reproduce scenario 4 for worker (see its README.md)
3. Kill all producers
```bash
pkill -f producer-0.0.1-SNAPSHOT.jar
```

4. Up 3 producers:
```bash
for p in 8011 8012 8013; do java -jar build/libs/producer-0.0.1-SNAPSHOT.jar --server.port=$p > producer-$p.log 2>&1 & done
```

5. Send start and stop commands to the producers
```bash
sleep 5
for p in 8011 8012 8013; do
  curl -X POST http://localhost:$p/start
done
sleep 30
for p in 8011 8012 8013; do
  curl -X POST http://localhost:$p/stop
done
echo "Done"
```

6. Check results by calling audit API
```bash
curl -X GET http://localhost:8030/count
````

#### Results

**Note, that we kept 8s processing on workers**

| Metric           | Count |
|------------------|------:|
| Produced tasks   |    45 |
| Processed tasks  |     0 |
| Certified tasks  |     0 |
| Discarded tasks  |    45 |

## Scenario 5

_1. Change the task String generation to a pattern {COLOR}-UUID where
COLOR is one of { RED, BLUE, GREEN } and produce a task String with
a different color each time (round robin is ok)_

_2. Deploy 3 instances of Audit, modified and configured in a way where
each service only keeps track of events of a single individual color that
it is configured to (e.g. Audit service configured to „RED” should only
register „RED” events)_

#### Reproducing

1. Reproduce scenario 5 for audit (see its README.md)
2. Launch any producers and workers with any processing time you want. Here 1 producer with 1 worker (1s processing) were used
3. Kill all producers
```bash
pkill -f producer-0.0.1-SNAPSHOT.jar
```

4. Up producer in IDE with setting `producer.use-colors: true`

5. Send start and stop commands to the producers
```bash
curl -X POST http://localhost:8010/start && \
sleep 30 && \
curl -X POST http://localhost:8010/stop && \
echo "Done"
```

6. Check results by calling audits APIs
```bash
echo "RED:   $(curl -s http://localhost:8031/count)" && \
echo "BLUE:  $(curl -s http://localhost:8032/count)" && \
echo "GREEN: $(curl -s http://localhost:8033/count)"
````

#### Results

| Color | Produced | Processed | Certified | Discarded |
|-------|---------:|----------:|----------:|----------:|
| RED   |        5 |         5 |         5 |         0 |
| BLUE  |        5 |         5 |         5 |         0 |
| GREEN |        5 |         5 |         5 |         0 |