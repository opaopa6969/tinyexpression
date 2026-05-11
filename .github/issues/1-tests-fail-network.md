# Failing Tests due to Network Reachability

Attempted to run `./mvnw test` on the `work` branch (master branch is missing). Maven wrapper failed to download required components due to lack of network connectivity:

```
Exception in thread "main" java.net.SocketException: Network is unreachable
```

This prevents executing the test suite.

## Proposed Fix

Provide an offline Maven repository or pre-populate the necessary dependencies to allow test execution without internet access. Alternatively, configure tests to run without requiring external downloads.
