package com.biometrics.controllers;

import com.biometrics.exceptions.ResponseException;
import com.biometrics.resources.LivenessResource;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Before;
import org.neogroup.warp.controllers.routing.Get;
import org.neogroup.warp.controllers.routing.Param;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.data.query.fields.SortDirection;
import org.neogroup.warp.http.*;
import org.neogroup.warp.resources.Resources;

import java.util.Collection;

import static org.neogroup.warp.Warp.getResponse;

@ControllerComponent("clients/:sessionId")
public class ClientsController {

    private static final String ADMINISTRATOR_SESSION_ID = "8cf3e790-6cd6-483b-9297-412b30d61328";

    @Before("*")
    public void checkSession(Request request, Response response) {
        String sessionId = request.get("sessionId");
        if (sessionId == null || sessionId.isEmpty()) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("SessionId not found !!");
        }
        if (!sessionId.equals(ADMINISTRATOR_SESSION_ID)) {
            response.setStatus(StatusCode.UNAUTHORIZED);
            throw new ResponseException("Invalid sessionId !!");
        }
    }

    @Get("liveness")
    public Collection<DataObject> getLivenessSessions(@Param("sessionId") String sessionId, @Param(name="limit", required = false) Integer limit) {
        Collection<DataObject> livenessSessions = Resources.get(LivenessResource.NAME).limit(limit != null? limit : 10).orderBy(LivenessResource.Fields.ID, SortDirection.DESC).select(LivenessResource.Fields.ID, LivenessResource.Fields.CLIENT_IP, LivenessResource.Fields.CLIENT_ID, LivenessResource.Fields.STATUS, LivenessResource.Fields.DATE, LivenessResource.Fields.SUCCESS, LivenessResource.Fields.VERSION).find();
        for (DataObject livenessSession : livenessSessions) {
            processLivenessSession(sessionId, livenessSession);
        }
        return livenessSessions;
    }

    @Get("liveness/:id")
    public DataObject getLivenessSession(@Param("sessionId") String sessionId, @Param("id") int livenessId) {
        DataObject livenessSession = Resources.get(LivenessResource.NAME).select(LivenessResource.Fields.ID, LivenessResource.Fields.DATE, LivenessResource.Fields.VERSION, LivenessResource.Fields.SUCCESS, LivenessResource.Fields.STATUS, LivenessResource.Fields.CLIENT_ID, LivenessResource.Fields.CLIENT_IP).where(LivenessResource.Fields.ID, livenessId).first();
        if (livenessSession == null) {
            throw new RuntimeException("Liveness session \"" + livenessId + "\" not found !!");
        }
        processLivenessSession(sessionId, livenessSession);
        return livenessSession;
    }

    @Get("liveness/:id/faceImage.jpeg")
    public byte[] getLivenessFaceImage(@Param("id") int livenessId) {
        DataObject livenessSession = Resources.get(LivenessResource.NAME).select(LivenessResource.Fields.FACE_IMAGE).where(LivenessResource.Fields.ID, livenessId).first();
        if (livenessSession == null) {
            throw new RuntimeException("Liveness session \"" + livenessId + "\" not found !!");
        }
        getResponse().addHeader(Header.CONTENT_TYPE, MediaType.IMAGE_JPEG);
        return livenessSession.get(LivenessResource.Fields.FACE_IMAGE);
    }

    @Get("liveness/:id/zoomedFaceImage.jpeg")
    public byte[] getLivenessZoomedFaceImage(@Param("id") int livenessId) {
        DataObject livenessSession = Resources.get(LivenessResource.NAME).select(LivenessResource.Fields.ZOOMED_FACE_IMAGE).where(LivenessResource.Fields.ID, livenessId).first();
        if (livenessSession == null) {
            throw new RuntimeException("Liveness session \"" + livenessId + "\" not found !!");
        }
        getResponse().addHeader(Header.CONTENT_TYPE, MediaType.IMAGE_JPEG);
        return livenessSession.get(LivenessResource.Fields.ZOOMED_FACE_IMAGE);
    }

    private void processLivenessSession(String sessionId, DataObject livenessSession) {
        int livenessId = livenessSession.get(LivenessResource.Fields.ID);
        livenessSession.set(LivenessResource.Fields.FACE_IMAGE, "/clients/" + sessionId + "/liveness/" + livenessId + "/faceImage.jpeg");
        livenessSession.set(LivenessResource.Fields.ZOOMED_FACE_IMAGE, "/clients/" + sessionId + "/liveness/" + livenessId + "/zoomedFaceImage.jpeg");
    }
}
