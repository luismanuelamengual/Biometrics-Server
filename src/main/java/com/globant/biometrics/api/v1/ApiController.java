package com.globant.biometrics.api.v1;

import com.globant.biometrics.models.BoundingBox;
import com.globant.biometrics.models.DocumentData;
import com.globant.biometrics.models.Face;
import com.globant.biometrics.utils.AmazonUtils;
import com.globant.biometrics.utils.DynamsoftUtils;
import com.globant.biometrics.utils.OpenCVUtils;
import org.neogroup.warp.controllers.ControllerComponent;
import org.neogroup.warp.controllers.routing.Parameter;
import org.neogroup.warp.controllers.routing.Post;
import org.neogroup.warp.data.Data;
import org.neogroup.warp.data.DataObject;

@ControllerComponent("v1")
public class ApiController {

    @Post("detect_face")
    public DataObject detectFace(@Parameter("image") byte[] image) throws Exception {
        Face face = OpenCVUtils.detectFace(image);
        DataObject faceObject = null;
        if (face != null) {
            BoundingBox boundingBox = face.getBoundingBox();
            faceObject = Data.object()
                .set("profile", face.getProfile().toString().toLowerCase())
                .set("boundingBox", Data.object()
                    .set("left", boundingBox.getLeft())
                    .set("top", boundingBox.getTop())
                    .set("width", boundingBox.getWidth())
                    .set("height", boundingBox.getHeight())
                );
        }
        return faceObject;
    }

    @Post("verify_identity")
    public DataObject verifyIdentity(@Parameter("selfie") byte[] selfie, @Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) {
        boolean match = false;
        float similarity = 0;
        if (AmazonUtils.isImageOfType(documentFront, "Document")) {
            similarity = AmazonUtils.compareFacesInImages (selfie, documentFront);
            if (similarity > 0) {
                match = true;
            }
        }
        if (!match) {
            if (AmazonUtils.isImageOfType(documentBack, "Document")) {
                similarity = AmazonUtils.compareFacesInImages (selfie, documentBack);
                if (similarity > 0) {
                    match = true;
                }
            }
        }
        DataObject resultObject = Data.object();
        resultObject.set("match", match);
        resultObject.set("similarity", similarity);
        return resultObject;
    }

    @Post("scan_document_data")
    public DataObject scanDocument(@Parameter("documentFront") byte[] documentFront, @Parameter("documentBack") byte[] documentBack) throws Exception {
        DocumentData documentData = DynamsoftUtils.readDocumentDataFromImageBarcode(documentFront);
        if (documentData == null) {
            documentData = DynamsoftUtils.readDocumentDataFromImageBarcode(documentBack);
        }
        if (documentData == null) {
            throw new RuntimeException("Document data could not be read");
        }
        DataObject result = Data.object()
            .set("firstName", documentData.getFirstName())
            .set("lastName", documentData.getLastName())
            .set("documentNumber", documentData.getDocumentNumber())
            .set("gender", documentData.getGender())
            .set("birthDate", documentData.getBirthDate())
            .set("nationalIdentificationNumber", documentData.getNationalIdentificationNumber());
        return result;
    }
}
