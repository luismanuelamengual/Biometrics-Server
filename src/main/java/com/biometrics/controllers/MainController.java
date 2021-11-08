package com.biometrics.controllers;

import com.biometrics.exceptions.ResponseException;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.After;
import org.neogroup.warp.controllers.routing.Error;
import org.neogroup.warp.controllers.routing.Get;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.http.Header;
import org.neogroup.warp.http.Request;
import org.neogroup.warp.http.Response;

import static org.neogroup.warp.Warp.getLogger;
import static org.neogroup.warp.Warp.getProperty;

@ControllerComponent
public class MainController {

    private static final String SUCCESS_PARAMETER_NAME = "success";
    private static final String DATA_PARAMETER_NAME = "data";
    private static final String MESSAGE_PARAMETER_NAME = "message";
    private static final String NAME_PARAMETER_NAME = "name";
    private static final String VERSION_PARAMETER_NAME = "version";

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
        if (exception instanceof ResponseException) {
            getLogger().info(result.toString());
        } else {
            getLogger().warn(result.toString());
        }
        return result;
    }

    @After
    public Object handleResponse (Request request, Response response) {
        Object responseObject = response.getResponseObject();
        if (!response.containsHeader(Header.CONTENT_TYPE)) {
            DataObject result = Data.object();
            result.set(SUCCESS_PARAMETER_NAME, true);
            if (responseObject != null) {
                result.set(DATA_PARAMETER_NAME, responseObject);
            }
            responseObject = result;
            getLogger().info(result.toString());
        }
        return responseObject;
    }
}
