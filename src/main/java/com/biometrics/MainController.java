package com.biometrics;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.neogroup.warp.Request;
import org.neogroup.warp.Response;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.formatters.JsonFormatter;
import org.neogroup.warp.controllers.routing.After;
import org.neogroup.warp.controllers.routing.Before;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.Get;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

import static org.neogroup.warp.Warp.getLogger;

@ControllerComponent
public class MainController {

    private static final String X_FORWARDED_FOR_HEADER_NAME = "X-Forwarded-For";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String AUTHORIZATION_BEARER = "Bearer";
    private static final String CLIENT_PARAMETER_NAME = "client";
    private static final String CLIENT_IP_PARAMETER_NAME = "clientIp";
    private static final String SUCCESS_PARAMETER_NAME = "success";
    private static final String DATA_PARAMETER_NAME = "data";
    private static final String MESSAGE_PARAMETER_NAME = "message";
    private static final String RESPONSE_PARAMETER_NAME = "response";
    private static final String METHOD_PARAMETER_NAME = "method";
    private static final String PATH_PARAMETER_NAME = "path";
    private static final String HEALTH_PARAMETER_NAME = "health";
    private static final String NAME_PARAMETER_NAME = "name";
    private static final String VERSION_PARAMETER_NAME = "version";

    private JsonFormatter jsonFormatter = new JsonFormatter();
    private String implementationTitle;
    private String implementationVersion;

    public MainController() {
        Package biometricsPackage = getClass().getPackage();
        implementationTitle = biometricsPackage.getImplementationTitle();
        implementationVersion = biometricsPackage.getSpecificationVersion();
    }

    @Before("v1/*")
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
            DecodedJWT verifiedToken = Authentication.decodeToken(token);
            request.set(CLIENT_PARAMETER_NAME, verifiedToken.getClaim(Authentication.CLIENT_CLAIM_NAME).asString());
            request.set(CLIENT_IP_PARAMETER_NAME, getClientIp(request));
        } catch (JWTVerificationException verificationException) {
            response.setStatus(401);
            throw new ResponseException(verificationException.getMessage());
        }
    }

    @Get("health_check")
    public DataObject checkHealth() {
        return Data.object().set(HEALTH_PARAMETER_NAME, 100);
    }

    @Get("about")
    public DataObject getAboutInformation() {
        return Data.object().set(NAME_PARAMETER_NAME, implementationTitle).set(VERSION_PARAMETER_NAME, implementationVersion);
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

    private DataObject getLogData(Request request, Object response) {
        DataObject data = Data.object();
        data.set(METHOD_PARAMETER_NAME, request.getMethod());
        data.set(PATH_PARAMETER_NAME, request.getRequestURI());
        data.set(VERSION_PARAMETER_NAME, implementationVersion);
        if (request.has(CLIENT_PARAMETER_NAME)) {
            data.set(CLIENT_PARAMETER_NAME, request.get(CLIENT_PARAMETER_NAME));
        }
        if (request.has(CLIENT_IP_PARAMETER_NAME)) {
            data.set(CLIENT_IP_PARAMETER_NAME, request.get(CLIENT_IP_PARAMETER_NAME));
        }
        data.set(RESPONSE_PARAMETER_NAME, response);
        return data;
    }

    private String getClientIp(Request request) {
        String clientIp = request.getHeader(X_FORWARDED_FOR_HEADER_NAME);
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddress();
        }
        return clientIp;
    }
}
