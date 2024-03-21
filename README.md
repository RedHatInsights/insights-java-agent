# Insights Java Client

This repo contains Java code for communicating with Red Hat Insights.

This capability is only available for Red Hat subscribers.

The only authentication flows we support in this release are:

1. mTLS using certs managed by Red Hat Subscription Manager (RHSM)
2. Bearer token authentication in OpenShift Container Platform (OCP)

This project contains a Java agent, supporting Java 8.

## Building

- Requires Java 8 (and no later)
- The [Spotless Maven Plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven) enforces code formatting conventions and will be run as part of the build.

```
$ mvn clean install
```

To skip the spotless task, run :

	$ mvn clean install -Dskip.spotless=true

Do not raise a PR without having run `spotless:apply` immediately prior.

## License Notes

This project contains some code comprising derivative works based upon open-source code originally released by New Relic.

The original work is also licensed under the Apache 2 License.


## Java agent args string

When using the agent in the startup configuration, the usual agent args string technique is used.
That is, the path to the agent jar is followed by an `=` and then the rest of the argument is passed as a single string to the agent.

In our case, the args are passed as key-value pairs, separated by `;`. For example:

```
-javaagent:runtimes-java-agent-1.0.0.jar=name=my_app;token=amXXXXYYYYZZZZj
```

Note that the use of `;` means that on Unix, means that the javaagent argument will typically need to be quoted.

The available key-value pairs are:

| Name         | Default value                     | Description                                                        |
|--------------|-----------------------------------|--------------------------------------------------------------------|
| `opt_out`    | `false`                           | Opt out of Red Hat Insights reporting when `true`                  |
| `name`       | N/A, must be defined              | Identification name for reporting                                  |
| `token`      | (empty)                           | Authentication token for token-based auth, if used                 |
| `token_file` | (empty)                           | File containing authentication token for token-based auth, if used |
| `base_url`   | `https://cert.console.redhat.com` | Server endpoint URL, overridden for OpenShift                      |
| `uri`        | `/api/ingress/v1/upload`          | Request URI at the server endpoint                                 |
| `proxy`      | (empty)                           | Proxy host, if any                                                 |
| `proxy_port` | (empty)                           | Proxy port, if any                                                 |

## Testing & coverage report

To run tests simply use maven command:
```
mvn clean test
```

This project is configured with JaCoCo coverage reporting, to get a coverage report run:
```
mvn clean test -Pcoverage
```

Report will be placed on:
```
(module)/target/site/jacoco/index.html
```
