package com.biometrics;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.neogroup.warp.Request;
import org.neogroup.warp.Response;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.formatters.JsonFormatter;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.*;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

import java.util.HashMap;
import java.util.Map;

import static org.neogroup.warp.Warp.getLogger;
import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private static final String BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME = "api_key_secret_key";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String AUTHORIZATION_BEARER = "Bearer";
    private static final String API_KEY_PARAMETER_NAME = "apiKey";
    private static final String SUCCESS_PARAMETER_NAME = "success";
    private static final String DATA_PARAMETER_NAME = "data";
    private static final String MESSAGE_PARAMETER_NAME = "message";

    private JWTVerifier jwtVerifier;
    private JsonFormatter jsonFormatter;

    public MainController() {
        String secretKey = getProperty(BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME);
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        jwtVerifier = JWT.require(algorithm).withIssuer("auth0").build();
        jsonFormatter = new JsonFormatter();
    }

    @Get("session")
    public String createSession(@Parameter("client") String clientName) throws Exception {
        String secretKey = getProperty(BIOMETRICS_JWT_SECRET_KEY_PROPERTY_NAME);
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create().withIssuer("auth0").withClaim("client", clientName).sign(algorithm);
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
            jwtVerifier.verify(token);
            request.set(API_KEY_PARAMETER_NAME, token);
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
        getLogger().info(jsonFormatter.format(result));
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
        getLogger().info(jsonFormatter.format(result));
        return result;
    }

    private Object getLogData(Object object) {
        Object logData = null;
        if (object instanceof DataObject) {
            Map<String, Object> properties = new HashMap<>();
            DataObject dataObject = (DataObject)object;
            for (String property : dataObject.properties()) {
                properties.put(property, getLogData(dataObject.get(property)));
            }
            logData = properties;
        } else {
            logData = object;
        }
        return logData;
    }
}
