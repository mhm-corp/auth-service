package com.mhm.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class JwtAuthentication implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Value("${jwt.auth.converter.principal-attribute}")
    private String principalAttribute;
    @Value("${jwt.auth.converter.resource-id}")
    private String resourceID;
    @Value("${jwt.auth.converter.claim}")
    private String claim;
    @Value("${jwt.auth.converter.client-roles}")
    private String clientRoles;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream
                .concat(
                        Optional.ofNullable(jwtGrantedAuthoritiesConverter.convert(jwt))
                                .orElse(List.of())
                                .stream(),
                        extractResourceRoles(jwt).stream()
                )
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, getPrincipalName(jwt));
    }

    private String getPrincipalName(Jwt jwt) {
        String claimName = JwtClaimNames.SUB;

        if (principalAttribute != null && !principalAttribute.isEmpty())
            claimName = principalAttribute;

        return jwt.getClaim(claimName);
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess;
        Map<String,Object> resource;
        Collection<String> resourceRoles;

        if (jwt.getClaim(claim) == null)
            return List.of();

        resourceAccess = jwt.getClaim(claim);

        if (resourceAccess.get(resourceID) == null)
            return List.of();

        resource = (Map<String, Object>) resourceAccess.get(resourceID);

        if (resource.get(clientRoles) == null)
            return List.of();

        resourceRoles = (Collection<String>) resource.get(clientRoles);

        return resourceRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_".concat(role)))
                .toList();
    }
}

