package com.biometrics.resources;

import org.neogroup.warp.Request;
import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.data.query.InsertQuery;
import org.neogroup.warp.resources.Resource;
import org.neogroup.warp.resources.ResourceComponent;

import java.util.Collection;
import java.util.Date;

import static org.neogroup.warp.Warp.*;

@ResourceComponent(LivenessResource.NAME)
public class LivenessResource extends Resource<DataObject> {

    public static final String NAME = "liveness";

    public static final class Fields {
        public static final String ID = "id";
        public static final String DATE = "date";
        public static final String VERSION = "version";
        public static final String CLIENT_ID = "clientid";
        public static final String CLIENT_IP = "clientip";
        public static final String FACE_IMAGE = "faceimage";
        public static final String ZOOMED_FACE_IMAGE = "zoomedfaceimage";
        public static final String SUCCESS = "success";
        public static final String STATUS = "status";
    }

    @Override
    public Collection<DataObject> insert(InsertQuery query) {
        query.set(Fields.DATE, new Date());
        query.set(Fields.VERSION, getProperty("appVersion"));
        Request request = getRequest();
        if (request != null) {
            query.set(Fields.CLIENT_ID, request.get("client"));
            query.set(Fields.CLIENT_IP, request.get("ip"));
        }
        getConnection().execute(query);
        return null;
    }
}
