package com.biometrics;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.neogroup.warp.Request;
import org.neogroup.warp.Response;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.*;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

import java.lang.reflect.InvocationTargetException;

import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private static final String BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME = "com.biometrics.jwt.secret_key";

    private JWTVerifier jwtVerifier;

    public MainController() {
        String secretKey = getProperty(BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME);
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        jwtVerifier = JWT.require(algorithm).withIssuer("auth0").build();
    }

    @Get("session")
    public String createSession(@Parameter("client") String clientName) throws Exception {
        String secretKey = getProperty(BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME);
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create().withIssuer("auth0").withClaim("client", clientName).sign(algorithm);
    }

    @Before("*")
    public void checkSession(Request request, Response response) {
        String requestUri = request.getRequestURI();
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null) {
            response.setStatus(401);
            throw new RuntimeException("Missing authorization header");
        }
        String[] authorizationTokens = authorizationHeader.split(" ");
        if (!authorizationTokens[0].equals("Bearer")) {
            response.setStatus(401);
            throw new RuntimeException("Authorization header is expecting a JWT token");
        }
        String token = authorizationTokens[1];
        try {
            jwtVerifier.verify(token);
        } catch (JWTVerificationException verificationException) {
            response.setStatus(401);
            throw new RuntimeException("Invalid authentication token");
        }
    }

    @Error("*")
    public DataObject errorHandler(Throwable exception) {
        while (exception instanceof InvocationTargetException) {
            exception = ((InvocationTargetException) exception).getTargetException();
        }
        DataObject result = Data.object();
        result.set("success", false);
        result.set("message", exception.getMessage());
        return result;
    }

    @After("*")
    public DataObject handleResponse (Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        DataObject result = Data.object();
        result.set("success", true);
        Object responseObject = response.getResponseObject();
        if (responseObject != null) {
            result.set("data", response.getResponseObject());
        }
        return result;
    }
}
