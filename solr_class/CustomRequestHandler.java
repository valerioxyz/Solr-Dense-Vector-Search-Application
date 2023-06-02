import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.common.util.NamedList;

import java.util.Random;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.common.util.ContentStream;

import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class CustomRequestHandler extends RequestHandlerBase {

    @Override
    public void init(NamedList args) {
        super.init(args);

        System.out.println("OINDAOGNADNGDOAI\n\n\n");
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        InputStream imageStream = null;
        try {
            // Extract the image from the request
            Iterable<ContentStream> contentStreams = req.getContentStreams();
            if (contentStreams != null) {
                for (ContentStream contentStream : contentStreams) {
                    imageStream = contentStream.getStream();

                    // Load the image using an image processing library
                    BufferedImage image = ImageIO.read(imageStream);

                    // Get the image resolution (width and height)
                    int width = image.getWidth();
                    int height = image.getHeight();

                    // Create a Solr document to hold the response
                    SolrInputDocument doc = new SolrInputDocument();
                    doc.addField("image_width", width);
                    doc.addField("image_height", height);

                    // Convert SolrInputDocument to Map<String, Object>
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.putAll(doc);

                    // Add the document to the response
                    rsp.add("image_resolution", docMap);
                }
            }
        } catch (IOException e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Error processing the image", e);
        } finally {
            IOUtils.closeWhileHandlingException(imageStream);
        }
    }

    @Override
    public String getDescription() {
        return "Custom Request Handler";
    }

    @Override
    public Name getPermissionName(AuthorizationContext context) {
        // Return the permission name based on the authorization context
        return null;
    }
}
