package com.biometrics;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.neogroup.warp.Request;
import org.neogroup.warp.Response;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.formatters.JsonFormatter;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.*;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

import static org.neogroup.warp.Warp.getLogger;
import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private static final String BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME = "api_key_secret_key";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String AUTHORIZATION_BEARER = "Bearer";
    private static final String CLIENT_PARAMETER_NAME = "client";
    private static final String SUCCESS_PARAMETER_NAME = "success";
    private static final String DATA_PARAMETER_NAME = "data";
    private static final String MESSAGE_PARAMETER_NAME = "message";
    private static final String RESPONSE_PARAMETER_NAME = "response";
    private static final String METHOD_PARAMETER_NAME = "method";
    private static final String PATH_PARAMETER_NAME = "path";

    private JWTVerifier jwtVerifier;
    private Algorithm jwtSigningAlgorithm;
    private JsonFormatter jsonFormatter;

    public MainController() {
        jwtSigningAlgorithm = Algorithm.HMAC256(getProperty(BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME));
        jwtVerifier = JWT.require(jwtSigningAlgorithm).withIssuer("auth0").build();
        jsonFormatter = new JsonFormatter();
    }

    public String createSession(String clientName) throws Exception {
        return JWT.create().withIssuer("auth0").withClaim(CLIENT_PARAMETER_NAME, clientName).sign(jwtSigningAlgorithm);
    }

    @Before("*")
    public void checkSession(Request request, Response response) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER_NAME);
        if (authorizationHeader == null) {
            response.setStatus(401);
            throw new ResponseException("Missing authorization header");
        }
        String[] authorizationTokens = authorizationHeader.split(" ");
        if (!authorizationTokens[0].equals(AUTHORIZATION_BEARER)) {
            response.setStatus(401);
            throw new ResponseException("Authorization header is expecting a JWT token");
        }
        if (authorizationTokens.length < 2) {
            response.setStatus(401);
            throw new ResponseException("Invalid authorization header");
        }
        String token = authorizationTokens[1];
        try {
            DecodedJWT verifiedToken = jwtVerifier.verify(token);
            request.set(CLIENT_PARAMETER_NAME, verifiedToken.getClaim(CLIENT_PARAMETER_NAME).asString());
        } catch (JWTVerificationException verificationException) {
            response.setStatus(401);
            throw new ResponseException("Invalid authentication token");
        }
    }

    @Error("*")
    public DataObject errorHandler(Request request, Throwable exception) {
        if (exception.getCause() != null) {
            exception = exception.getCause();
        }
        if (!(exception instanceof ResponseException)) {
            exception.printStackTrace();
        }
        DataObject result = Data.object();
        result.set(SUCCESS_PARAMETER_NAME, false);
        result.set(MESSAGE_PARAMETER_NAME, exception.getMessage());
        getLogger().info(jsonFormatter.format(getLogData(request, result)));
        return result;
    }

    @After("*")
    public DataObject handleResponse (Request request, Response response) {
        Object responseObject = response.getResponseObject();
        DataObject result = Data.object();
        result.set(SUCCESS_PARAMETER_NAME, true);
        if (responseObject != null) {
            result.set(DATA_PARAMETER_NAME, responseObject);
        }
        getLogger().info(jsonFormatter.format(getLogData(request, result)));
        return result;
    }

    public DataObject getLogData(Request request, Object response) {
        DataObject data = Data.object();
        data.set(METHOD_PARAMETER_NAME, request.getMethod());
        data.set(PATH_PARAMETER_NAME, request.getRequestURI());
        if (request.has(CLIENT_PARAMETER_NAME)) {
            data.set(CLIENT_PARAMETER_NAME, request.get(CLIENT_PARAMETER_NAME));
        }
        data.set(RESPONSE_PARAMETER_NAME, response);
        return data;
    }
}
