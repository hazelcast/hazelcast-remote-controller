package com.hazelcast.remotecontroller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.lingala.zip4j.ZipFile;
import org.python.jline.internal.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HazelcastCloudManager {
    private static final OkHttpClient client = new OkHttpClient();
    private String baseUrl;
    private URI uri;
    private final ObjectMapper mapper = new ObjectMapper();
    private String bearerToken;
    private static final Logger LOG = LogManager.getLogger(Main.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Call call;
    private final int timeoutForClusterStateWait = 5;

    public HazelcastCloudManager() {
    }

    public void loginToHazelcastCloudUsingEnvironment() throws CloudException {
        if(System.getenv("BASE_URL") != null && System.getenv("API_KEY") != null && System.getenv("API_SECRET") != null)
            loginToHazelcastCloud(System.getenv("BASE_URL"), System.getenv("API_KEY"), System.getenv("API_SECRET"));
        else
            throw new CloudException("Not all required environment variables are not set");
    }

    public void loginToHazelcastCloud(String url, String apiKey, String apiSecret) throws CloudException {
        uri = URI.create(url + "/api/v1");
        baseUrl = url;
        bearerToken = getBearerToken(apiKey, apiSecret);
        if(bearerToken == null)
            throw new CloudException("Login failed");
    }

    public CloudCluster createHazelcastCloudStandardCluster(String hazelcastVersion, boolean isTlsEnabled) throws CloudException {
        try {
            String clusterName = "test-cluster-" + UUID.randomUUID();
            String query = String.format("{\"query\":\"mutation {createStarterCluster(input: {name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" clusterType: SMALL totalMemory: 2 hazelcastVersion: \\\"%s\\\" isTlsEnabled: %b } ) { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token} } }\"}",
                    clusterName,
                    "aws",
                    "us-west-2",
                    hazelcastVersion,
                    isTlsEnabled);

            Log.info(String.format("Request query: %s", query));
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            JsonNode rootNode = mapper.readTree(responseBody).get("data").get("createStarterCluster");
            String clusterId = rootNode.get("id").asText();
            if(!waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait)))
                throw new Exception("Wait for cluster state is not finished as expected");
            return getHazelcastCloudCluster(clusterId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Create cluster failed, error message: " + e.getMessage());
        }
    }

    public boolean scaleUpDownHazelcastCloudStandardCluster(String clusterId, int scaleNumber) throws CloudException {
        int currentMemory;
        String requestUrl = String.format("%s/cluster/%s/updateMemory", baseUrl, clusterId);
        try {
            String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { specs {totalMemory} } }\" }", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            currentMemory = mapper.readTree(responseBody).get("data").get("cluster").get("specs").get("totalMemory").asInt();

            if (currentMemory + scaleNumber <= 0) {
                LOG.warn("Scaling number is not proper, there is no enough memory and nodes to scale down");
                return false;
            }

            String requestBody = String.format("{\"id\": %s, \"memory\": %d, \"autoScalingEnabled\": false}", clusterId, currentMemory + scaleNumber);
            RequestBody body = RequestBody.create(JSON, requestBody);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            responseBody = response.body().string();
            LOG.info(mapper.readTree(responseBody).get("state"));
            waitForStateOfCluster(clusterId,"RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Scale up/down is failed, error message is: " + e.getMessage());
        }
    }

    public CloudCluster getHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"query { cluster(clusterId: \\\"%s\\\") { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token}}}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            JsonNode rootNode = mapper.readTree(responseBody).get("data").get("cluster");
            if(rootNode.asText().equalsIgnoreCase("null"))
                return null;

            CloudCluster cluster = new CloudCluster(rootNode.get("id").asText(), rootNode.get("name").asText(), getConnectionName(rootNode.get("id").asText()), rootNode.get("hazelcastVersion").asText(), rootNode.get("isTlsEnabled").asBoolean(), rootNode.get("state").asText(), rootNode.get("discoveryTokens").elements().next().get("token").asText(), null, null);
            if(rootNode.get("isTlsEnabled").asBoolean())
            {
                cluster.setCertificatePath(downloadCertificatesAndGetPath(cluster.getId()));
                cluster.setTlsPassword(getTlsPassword(cluster.getId()));
            }
            return cluster;
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Get hazelcast cluster is failed, error message is: " + e.getMessage());
        }
    }

    public CloudCluster stopHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { stopCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            if(waitForStateOfCluster(clusterId, "STOPPED", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait)))
                return getHazelcastCloudCluster(clusterId);
            else
                throw new CloudException("State cannot become STOPPED");
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Stop hazelcast cloud is failed, error message is: " + e.getMessage());
        }
    }

    public CloudCluster resumeHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { resumeCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            if(waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait)))
                return getHazelcastCloudCluster(clusterId);
            else
                throw new CloudException("State cannot become RUNNING");
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Resume hazelcast cloud cluster is failed, error message is: " + e.getMessage());
        }
    }

    public boolean deleteHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { deleteCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(responseBody);
            waitForDeletedCluster(clusterId, TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            LOG.info(String.format("Cluster with id %s is deleted", clusterId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new CloudException("Delete hazelcast cloud cluster is failed, error message is: " + e.getMessage());
        }
    }


    private Response prepareAndSendRequest(String query) throws InterruptedException {
        int retryCountForExceptionOfEndpoint = 0;
        // Rarely server returns empty header, that is why a retry mechanism is added.
        while(retryCountForExceptionOfEndpoint < 3)
        {
            try {
                RequestBody body = RequestBody.create(JSON, query);
                Request request = new Request.Builder()
                        .url(HttpUrl.get(uri))
                        .post(body)
                        .header("Authorization", String.format("Bearer %s", bearerToken))
                        .header("Content-Type", "application/json")
                        .build();

                call = client.newCall(request);
                return call.execute();
            }
            catch(Exception e)
            {
                Log.warn(e.toString());
            }
            retryCountForExceptionOfEndpoint++;
            TimeUnit.SECONDS.sleep(2);
        }
        return null;
    }

    private String getTlsPassword(String clusterId)
    {
        String requestUrl = String.format("%s/cluster/%s", baseUrl, clusterId);
        try {
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .get()
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            try {
                return mapper.readTree(response.body().string()).get("tlsPassword").asText();
            } catch (IOException e) {
                LOG.warn("Body is null for tlsPassword");
                return null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private boolean waitForStateOfCluster(String clusterId, String expectedState, long timeoutInMillisecond) {
        String currentState = "";
        long startTime = System.currentTimeMillis();
        int retryCycle = 10;
        try {
            while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
                String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { id state } }\" }", clusterId);
                String responseBody = prepareAndSendRequest(query).body().string();
                LOG.info(responseBody);
                currentState = mapper.readTree(responseBody).get("data").get("cluster").get("state").asText();
                if(currentState.equalsIgnoreCase(expectedState))
                    return true;
                if(currentState.equalsIgnoreCase("FAILED"))
                {
                    LOG.error("Cluster is FAILED");
                    return false;
                }
                TimeUnit.SECONDS.sleep(retryCycle);
            }
            LOG.error(String.format("The cluster could not come to the given state in %d millisecond", timeoutInMillisecond));
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void waitForDeletedCluster(String clusterId, long timeoutInMillisecond) throws CloudException, InterruptedException {
        long startTime = System.currentTimeMillis();
        int retryCycle = 10;
        while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
            if (getHazelcastCloudCluster(clusterId) == null)
                return;
            TimeUnit.SECONDS.sleep(retryCycle);
        }
        throw new CloudException(String.format("The cluster is not deleted in %d millisecond", timeoutInMillisecond));
    }

    private String getBearerToken(String apiKey, String apiSecret) {
        try {
            String query = String.format("{ \"query\": \"mutation { login(apiKey: \\\"%s\\\", apiSecret: \\\"%s\\\") {token} }\" }", apiKey, apiSecret);
            RequestBody body = RequestBody.create(JSON, query);
            Request request = new Request.Builder()
                    .url(HttpUrl.get(uri))
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            String loginResponse = response.body().string();
            LOG.info(maskValueOfToken(loginResponse));
            return mapper.readTree(loginResponse).get("data").get("login").get("token").asText();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getConnectionName(String clusterId) {
        if (baseUrl.contains("uat.hazelcast.cloud"))
            return String.format("ua-%s", clusterId);
        else if (baseUrl.contains("cloud.hazelcast.com"))
            return String.format("pr-%s", clusterId);
        else {
            LOG.error("Given base url isn't known");
            return null;
        }
    }

    private String maskValueOfToken(String json)
    {
        return json.replaceAll("\"token\":\"(.*?)\"", "\"token\":\"******\"");
    }

    // It creates a folder with name clusterId under /home/user/
    // Then it downloads the certificates for the cluster and put them in the created folder
    private String downloadCertificatesAndGetPath(String clusterId)
    {
        try
        {
            String userHome = System.getProperty("user.home");
            Path pathClusterId = Paths.get(userHome, clusterId);

            // If folder with clusterId is there then certificates are already downloaded
            if(!Files.exists(pathClusterId))
            {
                createFolderAndDownloadCertificates(pathClusterId, clusterId);
            }
            return Paths.get(pathClusterId.toString(), "certificates").toString() + File.separator;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private void createFolderAndDownloadCertificates(Path pathClusterId, String clusterId) throws IOException {
        Path destination;
        Files.createDirectories(pathClusterId);
        Path pathResponseZip = Paths.get(pathClusterId.toString(), "certificates.zip");

        destination = Paths.get(pathClusterId.toString(), "certificates");
        Files.createDirectories(destination);

        String cert_url = String.format("%s/cluster/%s/certificate", baseUrl, clusterId);
        Request request = new Request.Builder()
                .url(cert_url)
                .get()
                .header("Accept", "application/zip")
                .header("Authorization", String.format("Bearer %s", bearerToken))
                .build();

        call = client.newCall(request);
        Response response = call.execute();
        try (FileOutputStream stream = new FileOutputStream(pathResponseZip.toString())) {
            stream.write(response.body().bytes());
        }
        ZipFile zipFile = new ZipFile(pathResponseZip.toString());
        zipFile.extractAll(destination.toString());
        new File(pathResponseZip.toString()).delete();
    }
}
