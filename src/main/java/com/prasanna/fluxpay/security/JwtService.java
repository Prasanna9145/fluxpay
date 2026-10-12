package com.prasanna.fluxpay.security;

import io.jsonwebtoken.Jwts;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.util.Date;

@Service
public class JwtService {

    private static final long EXPIRATION_TIME =
            60 * 60 * 1000;

    private final SecretKey signingKey;

    public JwtService(SecretKey signingKey) {
        this.signingKey = signingKey;
    }

    public String generateToken(String username) {

        Date now = new Date();

        Date expiration =
                new Date(
                        now.getTime() + EXPIRATION_TIME
                );

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(
                        signingKey,
                        Jwts.SIG.HS256
                )
                .compact();
    }
}