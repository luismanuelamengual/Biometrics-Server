package com.biometrics;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

import static org.neogroup.warp.Warp.getProperty;

public abstract class Authentication {

    public static final String CLIENT_CLAIM_NAME = "client";
    public static final String ALLOWED_IPS_CLAIM_NAME = "allowedIps";

    private static final String JWT_SECRET_KEY_PROPERTY_NAME = "api_key_secret_key";
    private static final Algorithm AUTHENTICATION_ALGORITHM = Algorithm.HMAC256(getProperty(JWT_SECRET_KEY_PROPERTY_NAME));
    private static final JWTVerifier AUTHENTICATION_VERIFIER = JWT.require(AUTHENTICATION_ALGORITHM).withIssuer("auth0").build();

    public static String createToken(String clientName, Date expirationDate) throws JWTCreationException {
        return createToken(clientName, expirationDate, null);
    }

    public static String createToken(String clientName, String[] allowedIps) throws JWTCreationException {
        return createToken(clientName, null, allowedIps);
    }

    public static String createToken(String clientName, Date expirationDate, String[] allowedIps) throws JWTCreationException {
        JWTCreator.Builder tokenBuilder = JWT.create().withIssuer("auth0");
        tokenBuilder.withClaim(CLIENT_CLAIM_NAME, clientName);
        if (expirationDate != null) {
            tokenBuilder.withExpiresAt(expirationDate);
        }
        if (allowedIps != null && allowedIps.length > 0) {
            tokenBuilder.withArrayClaim(ALLOWED_IPS_CLAIM_NAME, allowedIps);
        }
        return tokenBuilder.sign(AUTHENTICATION_ALGORITHM);
    }

    public static DecodedJWT decodeToken(String token) throws JWTVerificationException {
        return AUTHENTICATION_VERIFIER.verify(token);
    }
}
