# spring_cloud_config_demo
Spring Cloud Config Demo for Microservices

# Dynamic Refresh with Spring Cloud Config

This README explains how to configure and test **manual dynamic
refresh** in the `demo_config_client` and `demo_config_server`
applications. It uses a GitHub repository as the central configuration
source.

## 1. Architecture and configuration flow

``` text
                         GitHub repository
              https://github.com/ATULKUMARUTTAM/
                         spring_cloud_config_demo
                                  |
                 +----------------+----------------+
                 |                                 |
          configdemo.yml                  configdemo-prod.yml
          Default values                  Production values
          ID: 111                          ID: 222
          Version: 1.0.0                   Version: 2.0.0
                 |                                 |
                 +----------------+----------------+
                                  |
                                  v
                     Spring Cloud Config Server
                           localhost:8888
                                  |
                 Client requests application name
                       configdemo + profile prod
                                  |
                                  v
                       Spring Cloud Config Client
                           localhost:8080
                                  |
                     Configuration properties
                           BuildDemo bean
                                  |
                                  v
                       BuildController endpoint
                          GET /buildinfo
                                  |
                                  v
                   Build ID: 222, Version: 2.0.0,
                      Name: Production profile


       When GitHub configuration changes:
       1. Commit/push the new values to GitHub.
       2. POST /actuator/refresh on the client.
       3. Client fetches the current remote configuration.
       4. Spring refreshes/rebinds eligible beans.
       5. GET /buildinfo returns the updated values.
```

**Important:** `spring.application.name` must match the configuration
file prefix. The Java project can still be named `demo_config_client`;
the Spring application name should be `configdemo` because the remote
files are `configdemo.yml` and `configdemo-prod.yml`.

## 2. Repository files

The remote repository contains these files:

-   [`configdemo.yml`](https://github.com/ATULKUMARUTTAM/spring_cloud_config_demo/blob/main/configdemo.yml)
    --- default configuration.
-   [`configdemo-prod.yml`](https://github.com/ATULKUMARUTTAM/spring_cloud_config_demo/blob/main/configdemo-prod.yml)
    --- production-profile overrides.

Example `configdemo.yml`:

``` yaml
build:
  id: 111
  version: 1.0.0
  name: "Default profile"
```

Example `configdemo-prod.yml`:

``` yaml
build:
  id: 222
  version: 2.0.0
  name: "Production profile"
```

For the `prod` profile, the production file overrides properties with
the same keys in the default file.

## 3. Configure the Config Server

In the **Config Server** project's `src/main/resources/application.yml`,
use:

``` yaml
spring:
  application:
    name: demo_config_server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/ATULKUMARUTTAM/spring_cloud_config_demo.git

server:
  port: 8888
```

Make sure the Config Server project includes the Spring Cloud Config
Server dependency and has `@EnableConfigServer` on its main application
class.

Example main class:

``` java
@SpringBootApplication
@EnableConfigServer
public class DemoConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoConfigServerApplication.class, args);
    }
}
```

## 4. Configure the Config Client

In the **Config Client** project's `src/main/resources/application.yml`,
use:

``` yaml
spring:
  application:
    name: configdemo
  profiles:
    active: prod
  config:
    import: "configserver:http://localhost:8888"

management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
```

The `spring.config.import` setting is the modern Config Data approach.
If the application uses this form, `spring-cloud-starter-config` must be
on the client's classpath.

### Required client dependencies

Ensure the client `pom.xml` includes the Spring Cloud Config Client and
Spring Boot Actuator dependencies. Keep the versions managed by the
Spring Cloud release-train BOM that is compatible with your Spring Boot
version; do not choose unrelated Spring Cloud versions independently.

``` xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

If the Config Client dependency is already present, do not add it a
second time.

## 5. Bind remote properties to Java

The `BuildDemo` class can bind the remote `build.*` values using
`@ConfigurationProperties`:

``` java
package com.atuluttam.demo_config_client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "build")
@Data
public class BuildDemo {
    private String id;
    private String version;
    private String name;
}
```

Use `@Component` here to register the properties class as a bean. An
alternative is to keep `@Configuration` on the class or enable
configuration-properties scanning; use one clear bean-registration
approach rather than registering it multiple ways.

## 6. Add refresh scope to the properties bean

Add `@RefreshScope` to `BuildDemo` so that it can be recreated after a
refresh and read the latest values:

``` java
package com.atuluttam.demo_config_client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "build")
@RefreshScope
@Data
public class BuildDemo {
    private String id;
    private String version;
    private String name;
}
```

The controller can remain constructor-injected. For example:

``` java
package com.atuluttam.demo_config_client;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class BuildController {
    private final BuildDemo buildDemo;

    @GetMapping("/buildinfo")
    public String getBuildInfo() {
        return "Build ID: " + buildDemo.getId()
                + ", Version: " + buildDemo.getVersion()
                + ", Name: " + buildDemo.getName();
    }
}
```

If you prefer to put `@RefreshScope` on the controller instead, that is
a commonly used alternative for this simple demonstration. Do not assume
that annotating an arbitrary bean makes every other bean refresh
automatically; the properties bean must be refreshable/rebound
correctly.

## 7. Start and verify both applications

Start the applications in this order:

1.  Run the Config Server on port `8888`.
2.  Verify that the Config Server returns the expected remote
    properties.
3.  Run the Config Client on port `8080`.

Open this endpoint:

``` text
http://localhost:8888/configdemo/prod
```

The response should include `configdemo-prod.yml` and `configdemo.yml`
property sources, with the production values taking precedence for
overlapping keys.

Then open:

``` text
http://localhost:8080/buildinfo
```

Expected initial response:

``` text
Build ID: 222, Version: 2.0.0, Name: Production profile
```

If the result is `null`, check that the client's
`spring.application.name` is `configdemo` (not `demo_config_client`),
that the `build` prefix and property names match, and that the client
fetched the expected remote environment at startup.

## 8. Test dynamic refresh

### Step 1 --- Change the GitHub configuration

Edit `configdemo-prod.yml` in the repository and commit/push a new
version:

``` yaml
build:
  id: 333
  version: 3.0.0
  name: "Updated Production Profile"
```

Wait until the commit is visible on GitHub.

### Step 2 --- Trigger refresh on the client

Send a **POST** request to:

``` text
http://localhost:8080/actuator/refresh
```

Using PowerShell:

``` powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/actuator/refresh" `
  -Method Post
```

Or use Postman: - Method: `POST` - URL:
`http://localhost:8080/actuator/refresh` - Body: none

The response normally lists changed property keys, for example:

``` json
[
  "build.name",
  "build.version",
  "build.id"
]
```

The order and exact keys depend on what changed.

### Step 3 --- Verify the updated values

Call the endpoint again:

``` text
http://localhost:8080/buildinfo
```

Expected response:

``` text
Build ID: 333, Version: 3.0.0, Name: Updated Production Profile
```

The client should remain running; you should not need to restart it for
each configuration update.

## 9. Troubleshooting checklist

-   **`/buildinfo` returns `null`:** verify
    `spring.application.name: configdemo`,
    `spring.profiles.active: prod`, and the remote `build.*` keys.
-   **Config Server returns 404 or no expected property source:** check
    the Git URI, repository branch/label, exact file names, and whether
    the repository is accessible.
-   **`/actuator/refresh` returns 404:** confirm Actuator is on the
    classpath and `refresh` is included in
    `management.endpoints.web.exposure.include`.
-   **`/actuator/refresh` returns 401/403:** check Spring Security rules
    and authenticate or explicitly permit the endpoint as appropriate
    for your environment.
-   **Values do not change after refresh:** confirm the GitHub commit is
    available, send the POST to the **client** on port `8080` (not the
    Config Server on `8888`), inspect the refresh response and client
    logs, and ensure the configuration bean is refreshable/rebound.
-   **You changed `configdemo.yml` but see no change:** check whether
    `configdemo-prod.yml` overrides that same property.
-   **You changed GitHub but did not call refresh:** the client does not
    automatically poll GitHub in this setup. Trigger `/actuator/refresh`
    manually.
-   **The app starts with unexpected values:** check the active profile,
    the `spring.application.name`, and the Config Server response for
    that exact application name and profile.

## 10. Things to take care of

1.  **Application name must match the file prefix.** Use `configdemo`
    for `configdemo.yml` and `configdemo-prod.yml`.
2.  **Profiles control overrides.** With profile `prod`, values in
    `configdemo-prod.yml` override matching values in `configdemo.yml`.
3.  **Refresh is manual in this setup.** A GitHub commit alone does not
    notify the client.
4.  **Call the client endpoint.** Use
    `POST http://localhost:8080/actuator/refresh`.
5.  **Actuator security matters.** Exposing `refresh` over HTTP allows
    runtime configuration changes. For local learning, keep the service
    on a trusted machine/network. In a deployed environment, protect
    Actuator endpoints with authentication and network restrictions; do
    not expose refresh publicly.
6.  **Avoid exposing every Actuator endpoint.** Expose only the
    endpoints you need.
7.  **Keep secrets out of a public Git repository.** Do not commit
    passwords, tokens, private keys, or production credentials. Use a
    secure secret-management mechanism for sensitive values.
8.  **Compatibility matters.** Use a Spring Cloud release-train BOM
    compatible with the Spring Boot version in the project.
9.  **Refresh is not the same as automatic push.** For automatic
    propagation to multiple clients, consider Spring Cloud Bus with
    RabbitMQ or Kafka and a suitably secured webhook/event workflow.
    That is a separate setup.
10. **Refresh limitations exist.** Not every property or bean can be
    updated safely at runtime. Test any configuration that affects
    connection pools, server ports, or infrastructure resources before
    relying on refresh in production.

## 11. Quick command summary

  ------------------------------------------------------------------------------------------
  Purpose                 Method                  URL
  ----------------------- ----------------------- ------------------------------------------
  Inspect production      GET                     `http://localhost:8888/configdemo/prod`
  configuration                                   

  Check current client    GET                     `http://localhost:8080/buildinfo`
  values                                          

  Refresh client          POST                    `http://localhost:8080/actuator/refresh`
  configuration                                   

  Check client health (if GET                     `http://localhost:8080/actuator/health`
  exposed)                                        
  ------------------------------------------------------------------------------------------

**Expected result:** After you commit updated values to GitHub and send
`POST /actuator/refresh` to the client, `GET /buildinfo` should return
the updated values without restarting the client.
