# test-clients

Test utility of HTTP Clients implementations

## Build

### Pre-Requisites

* Java 11
* Maven 3

### Build the Jar

```
mvn clean install
```

This produces the `target/test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar` that can be used in any environment with Java to run out tests.

## Run

### Pre-Requisites

* Java 11

### How to Run

You can run the tool with the following command:

```
java -cp test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar com.dohbedoh.Main <requestURL>
```

This will send a request in a loop and print information about requests / response. The default interval between requests is `90` seconds. That give enough time to test changes in the environment (DNS, IP, Firewall, etc...).

### Arguments

* `requestURL`: the request URL to test against

### Environment variables

Following environment variables can be used to pass Basic authentication details to reach out to the test URL, if necessary:

* `TEST_USER`: the username
* `TEST_PASS`: the password

Note: Both must be set to use Basic authentication.

### System Properties

* `com.dohbedoh.requestInterval`: Interval between requests in Milliseconds. Default to `90000`.
* `com.dohbedoh.proxyHost`: An HTTP Proxy host address. Default to `null`.
* `com.dohbedoh.proxyPort`: An HTTP Proxy port. Default to `null`.
* `com.dohbedoh.recreateClients`: Recreate clients between attempts. Default to `false`, the same client is being used in the loop. 

## Examples

### Request URL

Test a request against a simple URL:

```
java -cp test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar com.dohbedoh.Main "https://api.github.com/rate_limit/"
```

With Docker:

```
docker run -ti --rm dohbedoh:test-clients:1.0 "https://api.github.com/rate_limit/"
```

With Kubernetes:

```
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-clients
spec:
  selector:
    matchLabels:
      app: test-clients
  replicas: 1
  template:
    metadata:
      labels:
        app: test-clients
    spec:
      containers:
      - name: test-clients
        image: dohbedoh/test-clients:1.0
        args:
          - "https://api.github.com/rate_limit"
```

### Request URL with Basic Authentication

Test a request against a simple URL with Basic authentication

```
TEST_USER=dohbedoh \
TEST_PASS=XXXXXXXXXXXXXXXXXXXXXXXX \
java -cp test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar com.dohbedoh.Main "https://api.github.com/rate_limit/"
```

With Docker:

```
docker run -ti --rm \
  -e TEST_USER=dohbedoh \
  -e TEST_PASS=XXXXXXXXXXXXXXXXXXXXXXXX \
  dohbedoh:test-clients:1.0 "https://api.github.com/rate_limit/"
```

With Kubernetes:

```
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-clients
spec:
  selector:
    matchLabels:
      app: test-clients
  replicas: 1
  template:
    metadata:
      labels:
        app: test-clients
    spec:
      containers:
      - name: test-clients
        image: dohbedoh/test-clients:1.0
        args:
          - "https://api.github.com/rate_limit"
        env:
          - name: TEST_USER
            value: dohbedoh
          - name: TEST_PASS
            value: XXXXXXXXXXXXXXXXXXXXXXXX
            # Better to create a kuberntes secret and use envFromSecret
```

### Request URL using an HTTP proxy

Test a request against a simple URL with an HTTP Proxy

```
java \
  -Dcom.dohbedoh.proxyHost="proxy.example.com" \
  -Dcom.dohbedoh.proxyPort=3128 \
  -cp test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar com.dohbedoh.Main "https://api.github.com/rate_limit/"
```

With Docker:

```
docker run -ti --rm \
  -e JAVA_OPTS="-Dcom.dohbedoh.proxyHost=proxy.example.com -Dcom.dohbedoh.proxyPort=3128" \
  dohbedoh:test-clients:1.0 "https://api.github.com/rate_limit/"
```

With Kubernetes:

```
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-clients
spec:
  selector:
    matchLabels:
      app: test-clients
  replicas: 1
  template:
    metadata:
      labels:
        app: test-clients
    spec:
      containers:
      - name: test-clients
        image: dohbedoh/test-clients:1.0
        args:
          - "https://api.github.com/rate_limit"
        env:
          - name: JAVA_OPTS
            value: "-Dcom.dohbedoh.proxyHost=squid-proxy -Dcom.dohbedoh.proxyPort=3128"
```

### Request URL with Basic Authentication ans using an HTTP proxy

Test a request against a simple URL with Basic authentication through an HTTP Proxy

```
TEST_USER=dohbedoh \
TEST_PASS=XXXXXXXXXXXXXXXXXXXXXXXX \
java \
  -Dcom.dohbedoh.proxyHost="proxy.example.com" \
  -Dcom.dohbedoh.proxyPort=3128 \
  -cp test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar com.dohbedoh.Main "https://api.github.com/rate_limit/"
```

With Docker:

```
docker run -ti --rm \
  -e TEST_USER=dohbedoh \
  -e TEST_PASS=XXXXXXXXXXXXXXXXXXXXXXXX \
  -e JAVA_OPTS="-Dcom.dohbedoh.proxyHost=proxy.example.com -Dcom.dohbedoh.proxyPort=3128" \
  dohbedoh:test-clients:1.0 "https://api.github.com/rate_limit/"
```

With Kubernetes:

```
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-clients
spec:
  selector:
    matchLabels:
      app: test-clients
  replicas: 1
  template:
    metadata:
      labels:
        app: test-clients
    spec:
      containers:
      - name: test-clients
        image: dohbedoh/test-clients:1.0
        args:
          - "https://api.github.com/rate_limit"
        env:
          - name: JAVA_OPTS
            value: "-Dcom.dohbedoh.proxyHost=squid-proxy -Dcom.dohbedoh.proxyPort=3128"
          - name: TEST_USER
            value: dohbedoh
            # Better to create a kuberntes secret and use envFromSecret
            # valueFrom:
            #   secretKeyRef:
            #     name: test-credentials
            #     key: test-user
          - name: TEST_PASS
            value: XXXXXXXXXXXXXXXXXXXXXXXX
            # Better to create a kuberntes secret and use envFromSecret
            # valueFrom:
            #   secretKeyRef:
            #     name: test-credentials
            #     key: test-password
```