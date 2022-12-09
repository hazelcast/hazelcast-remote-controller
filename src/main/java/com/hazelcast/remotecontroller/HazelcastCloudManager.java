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
import java.util.Arrays;
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
    private final int retryTimeInSecond = 10;

    public HazelcastCloudManager() {
    }

    public void loginToHazelcastCloudUsingEnvironment() throws CloudException {

        // StringUtil.isNullOrEmpty() can be used, but somehow it throws class not found exception. Needed to be investigated.
        String baseUrl = System.getenv("BASE_URL");
        String apiKey = System.getenv("API_KEY");
        String apiSecret = System.getenv("API_SECRET");
        String nullEnvVariables = "";
        if(baseUrl == null)
            nullEnvVariables = nullEnvVariables.concat(" BASE_URL ");
        if(apiKey == null)
            nullEnvVariables = nullEnvVariables.concat(" API_KEY ");
        if(apiSecret == null)
            nullEnvVariables = nullEnvVariables.concat(" API_SECRET ");

        if(nullEnvVariables == "")
            loginToHazelcastCloud(baseUrl, apiKey, apiSecret);
        else
            throw new CloudException("Not all required environment variables are set. These are not set ones: " + nullEnvVariables);
    }

    public void loginToHazelcastCloud(String url, String apiKey, String apiSecret) throws CloudException {
        uri = URI.create(url + "/api/v1");
        baseUrl = url;
        bearerToken = getBearerToken(apiKey, apiSecret);
        if(bearerToken == null)
            throw new CloudException("Login failed");
    }

    public CloudCluster createHazelcastCloudStandardCluster(String hazelcastVersion, boolean isTlsEnabled) throws CloudException {
        String clusterId = "";
        try {
            String clusterName = "test-cluster-" + UUID.randomUUID();
            String query = String.format("{\"query\":\"mutation {createStarterCluster(input: {name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" clusterType: SMALL totalMemory: 2 hazelcastVersion: \\\"%s\\\" isTlsEnabled: %b } ) { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token} } }\"}",
                    clusterName,
                    "aws",
                    "us-west-2",
                    hazelcastVersion,
                    isTlsEnabled);

            LOG.info(String.format("Request query: %s", query));
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            JsonNode rootNode = mapper.readTree(responseBody).get("data").get("createStarterCluster");
            clusterId = rootNode.get("id").asText();
            waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            return getHazelcastCloudCluster(clusterId);
        } catch (Exception e) {
            // Cluster id could be also empty if a problem occurred before cluster id exist
            throw new CloudException(String.format("Create cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    public void setHazelcastCloudClusterMemberCount(String clusterId, int totalMemberCount) throws CloudException {
        String requestUrl = String.format("%s/cluster/%s/updateMemory", baseUrl, clusterId);
        try {

            String requestBody = String.format("{\"id\": %s, \"memory\": %d, \"autoScalingEnabled\": false}", clusterId, totalMemberCount);
            RequestBody body = RequestBody.create(JSON, requestBody);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            String responseBody = response.body().string();
            LOG.info(mapper.readTree(responseBody).get("state"));
            waitForStateOfCluster(clusterId,"RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
        } catch (Exception e) {
            throw new CloudException(String.format("Set member size of cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
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
            throw new CloudException(String.format("Get cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    public CloudCluster stopHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { stopCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            waitForStateOfCluster(clusterId, "STOPPED", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            return getHazelcastCloudCluster(clusterId);
        } catch (Exception e) {
            throw new CloudException(String.format("Stop cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    public CloudCluster resumeHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { resumeCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            return getHazelcastCloudCluster(clusterId);
        } catch (Exception e) {
            throw new CloudException(String.format("Resume cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    public void deleteHazelcastCloudCluster(String clusterId) throws CloudException {
        try {
            String query = String.format("{\"query\": \"mutation { deleteCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = prepareAndSendRequest(query).body().string();
            LOG.info(responseBody);
            waitForDeletedCluster(clusterId, TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
            LOG.info(String.format("Cluster with id %s is deleted", clusterId));
        } catch (Exception e) {
            throw new CloudException(String.format("Delete hazelcast cloud cluster with id %s is failed, Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    private Response prepareAndSendRequest(String query) throws InterruptedException, CloudException {
        int retryCountForExceptionOfEndpoint = 0;
        Exception temp = null;
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
                temp = e;
                Log.warn(e.toString());
            }
            retryCountForExceptionOfEndpoint++;
            TimeUnit.SECONDS.sleep(2);
        }
        throw new CloudException(String.format("Request cannot send successfully after 3 tries, last exception stack trace is: %s", Arrays.toString(temp.getStackTrace())));
    }

    // Get tlsPassword method uses Rest API instead of GraphQL API.
    // In getHazelcastCloudCluster() method, first getting the cluster with GraphQL api and then if tls is enabled, certificate download and tls password requests are sent via Rest API.
    // During delete cluster process, in a corner case, getHazelcastCloudCluster() method works and cluster returns, but until getTlsPassword() method call, cluster is deleted.
    // That is why getTlsPassword() methods doesn't throw exception, it returns null. It shouldn't throw exception in this corner case
    // There cloud be improvement for this logic.
    private String getTlsPassword(String clusterId) {
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
            } catch (Exception e) {
                throw new CloudException("Body is null for tlsPassword. " + Arrays.toString(e.getStackTrace()));
            }
        }
        catch(Exception e)
        {
            LOG.warn(e);
            return null;
        }
    }

    private void waitForStateOfCluster(String clusterId, String expectedState, long timeoutInMillisecond) throws CloudException {
        String currentState = "";
        long startTime = System.currentTimeMillis();
        try {
            while (true) {
                String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { id state } }\" }", clusterId);
                String responseBody = prepareAndSendRequest(query).body().string();
                LOG.info(responseBody);
                currentState = mapper.readTree(responseBody).get("data").get("cluster").get("state").asText();
                if(currentState.equalsIgnoreCase(expectedState))
                    return;
                if(currentState.equalsIgnoreCase("FAILED"))
                {
                    throw new CloudException("Cluster is failed");
                }
                if((System.currentTimeMillis() - startTime) + TimeUnit.SECONDS.toMillis(retryTimeInSecond)  > timeoutInMillisecond)
                    break;
                TimeUnit.SECONDS.sleep(retryTimeInSecond);
            }
            throw new CloudException(String.format("The cluster with id %s could not come to the given state in %d millisecond", clusterId, timeoutInMillisecond));
        } catch (Exception e) {
            throw new CloudException(String.format("Wait for state of cluster with id %s failed. Rc stack trace is: %s", clusterId, Arrays.toString(e.getStackTrace())));
        }
    }

    // For a deleted cluster we cannot use the waitForStateOfCluster() method, its logic is different. That is why another method is added for delete cluster process
    private void waitForDeletedCluster(String clusterId, long timeoutInMillisecond) throws CloudException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (true) {
            if(getHazelcastCloudCluster(clusterId) == null)
                return;
            if((System.currentTimeMillis() - startTime) + TimeUnit.SECONDS.toMillis(retryTimeInSecond)  > timeoutInMillisecond)
                break;
            TimeUnit.SECONDS.sleep(retryTimeInSecond);
        }
        throw new CloudException(String.format("The cluster with id %s is not deleted in %d millisecond", clusterId, timeoutInMillisecond));
    }

    private String getBearerToken(String apiKey, String apiSecret) throws CloudException {
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
            throw new CloudException("Get bearer token is failed. " + Arrays.toString(e.getStackTrace()));
        }
    }

    // It is not possible to connect a cluster with its actual name, it seems a bug of the cloud API, that method is a workaround.
    // There could be a better way to get this name, TODO: contact with cloud team.
    private String getConnectionName(String clusterId) throws CloudException {
        if (baseUrl.contains("uat.hazelcast.cloud"))
            return String.format("ua-%s", clusterId);
        else if (baseUrl.contains("cloud.hazelcast.com"))
            return String.format("pr-%s", clusterId);
        else {
            throw new CloudException("Given base url isn't known and connection name cannot be created");
        }
    }

    // It replaces the  value of token with *** in a response body. Such as bearer token, discovery token.
    private String maskValueOfToken(String json)
    {
        return json.replaceAll("\"token\":\"(.*?)\"", "\"token\":\"******\"");
    }

    // It creates a folder with name clusterId under /home/user/
    // Then it downloads the certificates for the cluster and put them in the created folder
    private String downloadCertificatesAndGetPath(String clusterId) throws CloudException {
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
            throw new CloudException(String.format("Problem occurred during certificates download for cluster with id %s, Rc stack trace is: %s", clusterId, e.getStackTrace()));
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
        FileOutputStream stream = new FileOutputStream(pathResponseZip.toString());
        try {
            stream.write(response.body().bytes());
        } finally {
            stream.close();
        }

        ZipFile zipFile = new ZipFile(pathResponseZip.toString());
        zipFile.extractAll(destination.toString());
        new File(pathResponseZip.toString()).delete();
    }
}
