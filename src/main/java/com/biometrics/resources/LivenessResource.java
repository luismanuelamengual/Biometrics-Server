package com.biometrics.resources;

import org.neogroup.warp.data.DataObject;
import org.neogroup.warp.data.query.InsertQuery;
import org.neogroup.warp.data.query.SelectQuery;
import org.neogroup.warp.resources.Resource;
import org.neogroup.warp.resources.ResourceComponent;

import java.util.Collection;

import static org.neogroup.warp.Warp.getConnection;

@ResourceComponent(LivenessResource.NAME)
public class LivenessResource extends Resource<DataObject> {

    public static final String NAME = "liveness";

    public static final class Fields {
        public static final String ID = "id";
        public static final String DATE = "date";
        public static final String VERSION = "version";
        public static final String CLIENT_ID = "clientid";
        public static final String IP_ADDRESS = "ipaddress";
        public static final String FACE_IMAGE = "faceimage";
        public static final String ZOOMED_FACE_IMAGE = "zoomedfaceimage";
        public static final String SUCCESS = "success";
        public static final String STATUS = "status";
        public static final String HOST = "host";
        public static final String DEVICE = "device";
    }

    @Override
    public Collection<DataObject> insert(InsertQuery query) {
        getConnection().execute(query);
        return null;
    }

    @Override
    public Collection<DataObject> find(SelectQuery query) {
        return getConnection().query(query);
    }
}
