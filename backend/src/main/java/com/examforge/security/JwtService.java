package com.examforge.security;

import com.examforge.config.AppProperties;
import com.examforge.entity.Role;
import com.examforge.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Issues short-lived access tokens. Roles come from the database, never from the client. */
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final AppProperties props;

    public JwtService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTokenTtl()))
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles().stream().map(Role::getName).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return props.jwt().accessTokenTtl().toSeconds();
    }
}
