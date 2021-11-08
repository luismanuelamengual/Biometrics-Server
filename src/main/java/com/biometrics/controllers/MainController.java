package com.biometrics.controllers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.impl.NullClaim;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.biometrics.Authentication;
import com.biometrics.exceptions.ResponseException;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.After;
import org.neogroup.warp.controllers.routing.Before;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.Get;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.http.Header;
import org.neogroup.warp.http.Request;
import org.neogroup.warp.http.Response;
import org.neogroup.warp.http.StatusCode;
import org.neogroup.warp.utils.formatters.JsonFormatter;

import static org.neogroup.warp.Warp.getLogger;
import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private static final String BASE_PATH = "/";
    private static final char IP_SEPARATOR = ',';
    private static final String X_FORWARDED_FOR_HEADER_NAME = "X-Forwarded-For";
    private static final String AUTHORIZATION_BEARER = "Bearer";
    private static final String CLIENT_ID_PARAMETER_NAME = "client";
    private static final String IP_PARAMETER_NAME = "ip";
    private static final String SUCCESS_PARAMETER_NAME = "success";
    private static final String DATA_PARAMETER_NAME = "data";
    private static final String MESSAGE_PARAMETER_NAME = "message";
    private static final String RESPONSE_PARAMETER_NAME = "response";
    private static final String RESPONSE_TIME_PARAMETER_NAME = "responseTime";
    private static final String METHOD_PARAMETER_NAME = "method";
    private static final String PATH_PARAMETER_NAME = "path";
    private static final String NAME_PARAMETER_NAME = "name";
    private static final String VERSION_PARAMETER_NAME = "version";
    private static final String TIMESTAMP_PARAMETER_NAME = "timestamp";

    @Before("api/*")
    public void checkSession(Request request, Response response) {
        String authorizationHeader = request.getHeader(Header.AUTHORIZATION);
        if (authorizationHeader == null) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Missing authorization header");
        }
        String[] authorizationTokens = authorizationHeader.split(" ");
        if (!authorizationTokens[0].equals(AUTHORIZATION_BEARER)) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Authorization header is expecting a JWT token");
        }
        if (authorizationTokens.length < 2) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Invalid authorization header");
        }
        String token = authorizationTokens[1];
        try {
            DecodedJWT verifiedToken = Authentication.decodeToken(token);
            String ip = getClientIp(request);
            request.set(CLIENT_ID_PARAMETER_NAME, verifiedToken.getClaim(Authentication.CLIENT_ID_CLAIM_NAME).asInt());
            request.set(IP_PARAMETER_NAME, ip);
            Claim allowedIpsClaim = verifiedToken.getClaim(Authentication.ALLOWED_IPS_CLAIM_NAME);
            if (!(allowedIpsClaim instanceof NullClaim)) {
                String[] allowedIps = allowedIpsClaim.asArray(String.class);
                boolean allowed = false;
                for (String allowedIp : allowedIps) {
                    if (allowedIp.equals(ip)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new ResponseException("Ip \"" + ip + "\" is not allowed !!");
                }
            }
            request.set(TIMESTAMP_PARAMETER_NAME, System.currentTimeMillis());
        } catch (JWTVerificationException verificationException) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Invalid authorization token");
        }
    }

    @Get("/")
    public DataObject getAboutInformation() {
        return Data.object().set(NAME_PARAMETER_NAME, getProperty("appName")).set(VERSION_PARAMETER_NAME, getProperty("appVersion"));
    }

    @Error
    public DataObject errorHandler(Request request, Throwable exception) {
        if (exception.getCause() != null && exception.getCause().getMessage() != null) {
            exception = exception.getCause();
        }
        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            errorMessage = "Unknown error";
        }
        DataObject result = Data.object();
        result.set(SUCCESS_PARAMETER_NAME, false);
        result.set(MESSAGE_PARAMETER_NAME, errorMessage);
        if (!request.getRequestURI().equals(BASE_PATH)) {
            if (exception instanceof ResponseException) {
                getLogger().info(getLogData(request, result).toString());
            } else {
                getLogger().warn(getLogData(request, result).toString());
            }
        }
        return result;
    }

    @After
    public DataObject handleResponse (Request request, Response response) {
        Object responseObject = response.getResponseObject();
        DataObject result = Data.object();
        result.set(SUCCESS_PARAMETER_NAME, true);
        if (responseObject != null) {
            result.set(DATA_PARAMETER_NAME, responseObject);
        }
        if (!request.getRequestURI().equals(BASE_PATH)) {
            getLogger().info(getLogData(request, result).toString());
        }
        return result;
    }

    private DataObject getLogData(Request request, Object response) {
        DataObject data = Data.object();
        data.set(METHOD_PARAMETER_NAME, request.getMethod());
        data.set(PATH_PARAMETER_NAME, request.getRequestURI());
        data.set(VERSION_PARAMETER_NAME, getProperty("appVersion"));
        if (request.has(CLIENT_ID_PARAMETER_NAME)) {
            data.set(CLIENT_ID_PARAMETER_NAME, request.get(CLIENT_ID_PARAMETER_NAME));
        }
        if (request.has(IP_PARAMETER_NAME)) {
            data.set(IP_PARAMETER_NAME, request.get(IP_PARAMETER_NAME));
        }
        if (request.has(TIMESTAMP_PARAMETER_NAME)) {
            long requestTimestamp = request.get(TIMESTAMP_PARAMETER_NAME);
            long elapsedTime = System.currentTimeMillis() - requestTimestamp;
            data.set(RESPONSE_TIME_PARAMETER_NAME, elapsedTime / 1000.0);
        }
        data.set(RESPONSE_PARAMETER_NAME, response);
        return data;
    }

    private String getClientIp(Request request) {
        String clientIp = request.getHeader(X_FORWARDED_FOR_HEADER_NAME);
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddress();
        }
        int index = clientIp.indexOf(IP_SEPARATOR);
        if (index >= 0) {
            clientIp = clientIp.substring(0, index);
        }
        return clientIp;
    }
}
