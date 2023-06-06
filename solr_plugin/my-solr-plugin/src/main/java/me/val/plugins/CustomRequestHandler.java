package me.val.plugins;

import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.common.util.NamedList;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.ContentStream;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;

import org.apache.solr.common.SolrDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

//curl -X POST -H "Content-Type: image/png" --data-binary "@0070039.png" http://localhost:8983/solr/new_core123/custom
public class CustomRequestHandler extends RequestHandlerBase {
    private static boolean initialized = false;
    private static CNN_Model model;

    @Override
    public void init(NamedList args) {
        super.init(args);

        if (!initialized) {
            // Esegui l'azione una sola volta qui
            initialized = true;
            try {
                String savedModelPath = extractModelFromResource();
                model = new CNN_Model(savedModelPath);
            } catch (IOException e) {
                System.out.println(e);
            } catch (URISyntaxException e) {
                System.out.println(e);
            }
        }
    }

    public static String extractModelFromResource() throws IOException, URISyntaxException {
        String resourceZipPath = "saved_model.zip"; // Path to the zip file in "resources" folder
        System.out.println("[APACHE SOLR PLUGIN] temp folder is " + System.getProperty("java.io.tmpdir"));
        String destinationFolderPath = System.getProperty("java.io.tmpdir") + "\\saved_model"; // Destination folder
                                                                                               // path

        URL resourceZipUrl = CustomRequestHandler.class.getClassLoader().getResource(resourceZipPath);
        if (resourceZipUrl == null) {
            throw new IllegalArgumentException("Resource zip file not found");
        }

        // Create the destination folder if it doesn't exist
        File destinationFolder = new File(destinationFolderPath);
        if (destinationFolder.exists()) {
            System.out.println("[APACHE SOLR PLUGIN] FOLDER ALREADY EXISTS, SKIPPING UNZIP..");
            return destinationFolderPath;
        }
        destinationFolder.mkdirs();

        // Extract the zip file
        try (ZipInputStream zipInputStream = new ZipInputStream(
                CustomRequestHandler.class.getClassLoader().getResourceAsStream(resourceZipPath))) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                String entryPath = destinationFolderPath + File.separator + entryName;

                if (entry.isDirectory()) {
                    File dirToCreate = new File(entryPath);
                    dirToCreate.mkdirs();
                } else {
                    try (FileOutputStream outputStream = new FileOutputStream(entryPath)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }

        System.out.println("[APACHE SOLR PLUGIN] Model extraction completed");
        return destinationFolderPath;
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
                    doc.addField("width", width);
                    doc.addField("height", height);

                    float[] result = model.calculateFeatureVector(image);

                    Float[] featureVector = new Float[result.length];
                    for (int i = 0; i < result.length; i++) {
                        featureVector[i] = result[i];
                    }

                    doc.addField("feature_vector", featureVector);

                    // Convert SolrInputDocument to Map<String, Object>
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.putAll(doc);

                    rsp.add("image_info", docMap);
                    
                    String solrUrl = "http://localhost:8983/solr";
                    String collectionName = "new_core123";
                    int topK = 10;

                    List<SolrDocument> searchResults = searchDocuments(solrUrl, collectionName, featureVector, topK);

                    //Map<String, Object> documentAndSimilarities = new HashMap<>();
                    List<Map<String, Object>> documentsAndSimilarities = new ArrayList<>();

                    for (SolrDocument retrieved_doc : searchResults) {
                        List<String> documentVectorObj = (List<String>) retrieved_doc.getFieldValue("feature_vector");

                        Float[] documentVector = documentVectorObj.stream()
                                .map(Float::valueOf)
                                .toArray(Float[]::new);

                        double similarityScore = calculateCosineSimilarity(featureVector, documentVector);

                        // Creazione del documento corrente come un oggetto Map
                        Map<String, Object> document = new HashMap<>();
                        document.put("document", retrieved_doc);
                        document.put("similarity", similarityScore);

                        // Aggiunta del documento corrente alla lista dei documenti e similarità
                        documentsAndSimilarities.add(document);
                    }

                    // Aggiunta della lista dei documenti e similarità alla SolrQueryResponse
                    rsp.add("documents", documentsAndSimilarities);

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

    // Calculate cosine similarity between two vectors
    private static double calculateCosineSimilarity(Float[] vector1, Float[] vector2) {
        double dotProduct = 0.0;
        double magnitudeVector1 = 0.0;
        double magnitudeVector2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            magnitudeVector1 += Math.pow(vector1[i], 2);
            magnitudeVector2 += Math.pow(vector2[i], 2);
        }

        magnitudeVector1 = Math.sqrt(magnitudeVector1);
        magnitudeVector2 = Math.sqrt(magnitudeVector2);

        if (magnitudeVector1 == 0.0 || magnitudeVector2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (magnitudeVector1 * magnitudeVector2);
    }

    public static List<SolrDocument> searchDocuments(String solrUrl, String collectionName, Float[] featureVector,
            int topK) {
        List<SolrDocument> results = new ArrayList<>();

        try {
            // Create a SolrClient instance
            SolrClient solrClient = new HttpSolrClient.Builder(solrUrl + "/" + collectionName).build();

            // Create a SolrQuery instance
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery("{!knn f=feature_vector topK=" + topK + "}" + featureVectorToString(featureVector));

            // Execute the query
            QueryResponse response = solrClient.query(solrQuery);

            // Get the result documents
            SolrDocumentList solrDocuments = response.getResults();

            // Add the documents to the results list
            results.addAll(solrDocuments);

            // Close the SolrClient
            solrClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    // Utility method to convert Float[] to String
    private static String featureVectorToString(Float[] featureVector) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        for (int i = 0; i < featureVector.length; i++) {
            if (i > 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(featureVector[i]);
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
