# lp-api

Consumer easily Liveperson apis using this library.

## Domains Resultion

```java
    domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
```

## Service apis

```java
    final Idp apiEndpoint = GeneralAPI.apiEndpoint(domains, Idp.class);
```

    
