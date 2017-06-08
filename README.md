# lp-api

Consume easily Liveperson apis using this library.

## Domains resolution

```java
domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
```

## Services apis

Get instance of the service api endpoint using the ``apiEndpoint`` call.
Here is an example of getting endpoint for the ``IDP`` service.

```java
final Idp apiEndpoint = GeneralAPI.apiEndpoint(domains, Idp.class);
```

    
