package com.globant.biometrics.models;

public class Face {

    private FaceProfile profile;
    private BoundingBox boundingBox;

    public Face(BoundingBox boundingBox, FaceProfile profile) {
        this.profile = profile;
        this.boundingBox = boundingBox;
    }

    public FaceProfile getProfile() {
        return profile;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }
}
