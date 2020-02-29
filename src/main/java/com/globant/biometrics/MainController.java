package com.globant.biometrics;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.globant.biometrics.utils.AmazonUtils;
import com.globant.biometrics.utils.DynamsoftUtils;
import com.globant.biometrics.utils.OpenCVUtils;
import org.neogroup.warp.Request;
import org.neogroup.warp.Response;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.*;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private JWTVerifier jwtVerifier;

    public MainController() {
        String secretKey = getProperty("jwt_secret_key");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        jwtVerifier = JWT.require(algorithm).withIssuer("auth0").build();
    }

    @Get("session")
    public DataObject createSession(@Parameter("client") String clientName) throws Exception {
        DataObject result = Data.object();
        String secretKey = getProperty("jwt_secret_key");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String token = JWT.create().withIssuer("auth0").withClaim("client", clientName).sign(algorithm);
        result.set("success", true);
        result.set("token", token);
        return result;
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
        DataObject result = Data.object();
        result.set("success", false);
        result.set("message", exception.getMessage());
        return result;
    }

    @After("*")
    public void handleResponse (Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
    }
}
