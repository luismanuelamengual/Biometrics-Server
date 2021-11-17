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

    public static final String CLIENT_ID_CLAIM_NAME = "clientId";
    public static final String ALLOWED_HOSTS_CLAIM_NAME = "allowedHosts";
    public static final String ALLOWED_IPS_CLAIM_NAME = "allowedIps";

    private static final String JWT_SECRET_KEY_PROPERTY_NAME = "api_key_secret_key";
    private static final Algorithm AUTHENTICATION_ALGORITHM = Algorithm.HMAC256(getProperty(JWT_SECRET_KEY_PROPERTY_NAME));
    private static final JWTVerifier AUTHENTICATION_VERIFIER = JWT.require(AUTHENTICATION_ALGORITHM).withIssuer("auth0").build();

    public static String createToken(int clientId) throws JWTCreationException {
        return createToken(clientId, null, null, null);
    }

    public static String createToken(int clientId, Date expirationDate) throws JWTCreationException {
        return createToken(clientId, expirationDate, null, null);
    }

    public static String createToken(int clientId, Date expirationDate, String[] allowedHosts) throws JWTCreationException {
        return createToken(clientId, expirationDate, allowedHosts, null);
    }

    public static String createToken(int clientId, String[] allowedHosts) throws JWTCreationException {
        return createToken(clientId, null, allowedHosts, null);
    }

    public static String createToken(int clientId, Date expirationDate, String[] allowedHosts, String[] allowedIps) throws JWTCreationException {
        JWTCreator.Builder tokenBuilder = JWT.create().withIssuer("auth0");
        tokenBuilder.withClaim(CLIENT_ID_CLAIM_NAME, clientId);
        if (expirationDate != null) {
            tokenBuilder.withExpiresAt(expirationDate);
        }
        if (allowedHosts != null && allowedHosts.length > 0) {
            tokenBuilder.withArrayClaim(ALLOWED_HOSTS_CLAIM_NAME, allowedHosts);
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
