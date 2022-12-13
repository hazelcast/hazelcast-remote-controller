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
    private final ObjectMapper mapper = new ObjectMapper();
    private String bearerToken;
    private static final Logger LOG = LogManager.getLogger(Main.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Call call;
    private static final int timeoutForClusterStateWait = 5;
    private static final int retryTimeInSecond = 10;
    private static final int clusterTypeId = 5; // Serverless cluster
    private static final int cloudProviderId = 1; // aws
    private static final int cloudProviderRegionId = 4; // us-west-2
    private static final String clusterPlan = "SERVERLESS"; // us-west-2

    public HazelcastCloudManager() {
    }

    public void loginToCloudUsingEnvironment() throws CloudException {

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
            loginToCloud(baseUrl, apiKey, apiSecret);
        else
            throw new CloudException("Not all required environment variables are set. These are not set ones: " + nullEnvVariables);
    }

    public void loginToCloud(String url, String apiKey, String apiSecret) throws CloudException {
        baseUrl = url;
        bearerToken = getBearerToken(apiKey, apiSecret);
        if(bearerToken == null)
            throw new CloudException("Login failed");
    }

    public CloudCluster createCloudCluster(String hazelcastVersion, boolean isTlsEnabled) throws CloudException {
        String clusterId = "";
        try {
            String clusterName = "test-cluster-" + UUID.randomUUID();
            String jsonString = String.format("{" +
                            "  \"name\": \"%s\"," +
                            "  \"clusterTypeId\": %d," + // Serverless cluster
                            "  \"cloudProviders\": [%d]," + // aws
                            "  \"regions\": [%d]," + // us-west-2
                            "  \"planName\": \"%s\"," +
                            "  \"hazelcastVersion\": \"%s\"," +
                            "  \"tlsEnabled\": %b" +
                            "}",
                    clusterName,
                    clusterTypeId,
                    cloudProviderId,
                    cloudProviderRegionId,
                    clusterPlan,
                    hazelcastVersion,
                    isTlsEnabled);

            LOG.info(String.format("Request query: %s", jsonString));
            try (Response response = sendPostRequest("/cluster", jsonString)){
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while creating a dev mode cluster: %s", response));
                }
                String responseString = responseBody.string();
                handleFailedResponse(response, responseString);
                LOG.info(maskValueOfToken(responseString));
                clusterId = mapper.readTree(responseString).get("id").asText();
                waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
                return getCloudCluster(clusterId);
            }
        } catch (Exception e) {
            // Cluster id could be also empty if a problem occurred before cluster id exist
            throw new CloudException(String.format("Create cluster with id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    private static String getErrorString(Exception e) {
        return e + "\n" + Arrays.toString(e.getStackTrace());
    }

    private static void handleFailedResponse(Response response, String responseString) throws CloudException {
        if (!response.isSuccessful()) {
            String errorString = String.format("Unexpected http code %d Response: %s", response.code(), responseString);
            LOG.error(errorString);
            throw new CloudException(errorString);
        }
    }

    public CloudCluster getCloudCluster(String clusterId) throws CloudException {
        return getCloudCluster(clusterId, true);
    }

    public CloudCluster getCloudCluster(String clusterId, boolean setupTls) throws CloudException {
        try {
            try (Response res = sendGetRequest(String.format("/cluster/%s", clusterId))) {
                ResponseBody responseBody = res.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while getting a cluster: %s", res));
                }
                String responseString = responseBody.string();
                try {
                    handleFailedResponse(res, responseString);
                } catch (CloudException e) {
                    return null;
                }
                LOG.info(maskValueOfToken(responseString));
                JsonNode rootNode = mapper.readTree(responseString);
                if(rootNode.asText().equalsIgnoreCase("null"))
                    return null;
                boolean tlsEnabled = rootNode.get("tlsEnabled").asBoolean();
                CloudCluster cluster = new CloudCluster(rootNode.get("id").asText(), rootNode.get("name").asText(), rootNode.get("releaseName").asText(), rootNode.get("hazelcastVersion").asText(), tlsEnabled, rootNode.get("state").asText(), rootNode.get("tokens").elements().next().get("token").asText(), null, null);
                if(tlsEnabled && setupTls)
                {
                    cluster.setCertificatePath(downloadCertificatesAndGetPath(cluster.getId()));
                    cluster.setTlsPassword(getTlsPassword(cluster.getId()));
                }
                return cluster;
            }

        } catch (Exception e) {
            throw new CloudException(String.format("Get cluster with id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    public CloudCluster stopCloudCluster(String clusterId) throws CloudException {
        try {
            try (Response res = sendPostRequest(String.format("/cluster/%s/stop", clusterId), null)) {
                ResponseBody responseBody = res.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while stopping a cluster: %s", res));
                }
                String responseString = responseBody.string();
                handleFailedResponse(res, responseString);
                LOG.info(maskValueOfToken(responseString));
                waitForStateOfCluster(clusterId, "STOPPED", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
                return getCloudCluster(clusterId);
            }
        } catch (Exception e) {
            throw new CloudException(String.format("Stop cluster with id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    public CloudCluster resumeCloudCluster(String clusterId) throws CloudException {
        try {
            try (Response res = sendPostRequest(String.format("/cluster/%s/resume", clusterId), null)) {
                ResponseBody responseBody = res.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while resuming a cluster: %s", res));
                }
                String responseString = responseBody.string();
                handleFailedResponse(res, responseString);
                LOG.info(maskValueOfToken(responseString));
                waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
                return getCloudCluster(clusterId);
            }
        } catch (Exception e) {
            throw new CloudException(String.format("Resume cluster with id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    public void deleteCloudCluster(String clusterId) throws CloudException {
        try {
            try (Response res = sendDeleteRequest(String.format("/cluster/%s", clusterId))) {
                ResponseBody responseBody = res.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while deleting a cluster: %s", res));
                }
                String responseString = responseBody.string();
                handleFailedResponse(res, responseString);
                LOG.info(responseString);
                waitForDeletedCluster(clusterId, TimeUnit.MINUTES.toMillis(timeoutForClusterStateWait));
                LOG.info(String.format("Cluster with id %s is deleted", clusterId));
            }
        } catch (Exception e) {
            throw new CloudException(String.format("Delete hazelcast cloud cluster with id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    private Response sendPostRequest(String endpoint, String jsonString) throws CloudException {
        URI uri = URI.create(baseUrl + endpoint);
        try {
            String bearerTokenHeader = String.format("Bearer %s", bearerToken);
            LOG.warn(bearerTokenHeader);
            Request.Builder reqBuilder = new Request.Builder()
                    .url(HttpUrl.get(uri))
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .header("Content-Type", "application/json");
            if(jsonString != null) {
                RequestBody body = RequestBody.create(JSON, jsonString);
                reqBuilder.post(body);
            } else {
                reqBuilder.post(RequestBody.create(null, new byte[0]));
            }
            Request request = reqBuilder.build();
            call = client.newCall(request);
            return call.execute();
        }
        catch(Exception e) {
            Log.warn(e.toString());
            throw new CloudException(String.format("Exception while sending a post request to %s with body %s: \n %s",
                    uri, jsonString, getErrorString(e)));
        }
    }

    private Response sendGetRequest(String endpoint) throws CloudException {
        URI uri = URI.create(baseUrl + endpoint);
        try {
            Request request = new Request.Builder()
                    .url(HttpUrl.get(uri))
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .header("Content-Type", "application/json")
                    .get()
                    .build();
            call = client.newCall(request);
            return call.execute();
        }
        catch(Exception e) {
            Log.warn(e.toString());
            throw new CloudException(String.format("Exception while sending a get request to %s: \n %s", uri, getErrorString(e)));
        }
    }

    private Response sendDeleteRequest(String endpoint) throws CloudException {
        URI uri = URI.create(baseUrl + endpoint);
        try {
            Request request = new Request.Builder()
                    .url(HttpUrl.get(uri))
                    .delete()
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .header("Content-Type", "application/json")
                    .build();

            call = client.newCall(request);
            return call.execute();
        }
        catch(Exception e) {
            Log.warn(e.toString());
            throw new CloudException(String.format("Exception while sending a delete request to %s: \n %s", uri, getErrorString(e)));
        }
    }

    // Get tlsPassword method uses Rest API instead of GraphQL API.
    // In getCluster() method, we first get the cluster and then if tls is enabled, certificate download and tls password requests are sent.
    // During delete cluster process, the following is possible getCluster() returns the cluster, but until getTlsPassword() method is called,
    // the cluster is deleted. That is why getTlsPassword() methods doesn't throw exception, it returns null. It shouldn't throw exception in this corner case
    // There cloud be improvement for this logic.
    private String getTlsPassword(String clusterId) throws CloudException {
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
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new CloudException(String.format("Response body is null while getting tls password for cluster: %s", response));
                }
                String responseString = responseBody.string();
                handleFailedResponse(response, responseString);
                return mapper.readTree(responseString).get("tlsPassword").asText();
            } catch (Exception e) {
                throw new CloudException("Body is null for tlsPassword. Rc error is: " + getErrorString(e));
            }
        }
        catch(Exception e) {
            LOG.warn(e);
            throw new CloudException(String.format("Get tls password with cluster id %s is failed, Rc error is: %s", clusterId, getErrorString(e)));
        }
    }

    private void waitForStateOfCluster(String clusterId, String expectedState, long timeoutInMillisecond) throws CloudException {
        String currentState;
        long startTime = System.currentTimeMillis();
        try {
            while (true) {
                try (Response response = sendGetRequest(String.format("/cluster/%s", clusterId))){
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        throw new CloudException(String.format("Response body is null while waiting for state of a cluster: %s", response));
                    }
                    String responseString = responseBody.string();
                    handleFailedResponse(response, responseString);
                    LOG.info(responseString);
                    currentState = mapper.readTree(responseString).get("state").asText();
                    if(currentState.equalsIgnoreCase(expectedState))
                        return;
                    if(currentState.equalsIgnoreCase("FAILED"))
                    {
                        throw new CloudException(String.format("Cluster state is failed, state response: %s", responseString));
                    }
                    if((System.currentTimeMillis() - startTime) + TimeUnit.SECONDS.toMillis(retryTimeInSecond)  > timeoutInMillisecond)
                        break;
                    TimeUnit.SECONDS.sleep(retryTimeInSecond);
                }
            }
            throw new CloudException(String.format("The cluster with id %s did not end up in the %s state in %d milliseconds", clusterId, expectedState, timeoutInMillisecond));
        } catch (Exception e) {
            throw new CloudException(String.format("An error occurred while waiting for the %s state of the cluster with id %s. Error is: %s", expectedState, clusterId, getErrorString(e)));
        }
    }

    // For a deleted cluster we cannot use the waitForStateOfCluster() method, its logic is different. That is why another method is added for delete cluster process
    private void waitForDeletedCluster(String clusterId, long timeoutInMillisecond) throws CloudException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (true) {
            // Don't setup tls, we just want to check if cluster is deleted
            if(getCloudCluster(clusterId, false) == null)
                return;
            if((System.currentTimeMillis() - startTime) + TimeUnit.SECONDS.toMillis(retryTimeInSecond)  > timeoutInMillisecond)
                break;
            TimeUnit.SECONDS.sleep(retryTimeInSecond);
        }
        throw new CloudException(String.format("The cluster with id %s is not deleted in %d millisecond", clusterId, timeoutInMillisecond));
    }

    private String getBearerToken(String apiKey, String apiSecret) throws CloudException {
        try {
            String query = String.format("{ \"apiKey\": \"%s\", \"apiSecret\": \"%s\" }", apiKey, apiSecret);
            LOG.info(String.format("Sending query %s for getting bearer token", query));
            RequestBody body = RequestBody.create(JSON, query);
            Request request = new Request.Builder()
                    .url(baseUrl + "/customers/api/login")
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new CloudException(String.format("Response body is null while getting bearer token: %s", response));
            }
            String responseString = responseBody.string();
            handleFailedResponse(response, responseString);
            LOG.info(maskValueOfToken(responseString));
            return mapper.readTree(responseString).get("token").asText();
        } catch (Exception e) {
            throw new CloudException("Get bearer token is failed. " + getErrorString(e));
        }
    }

    // It replaces the value of token with *** in a response body. Such as bearer token, discovery token.
    private String maskValueOfToken(String json)
    {
        return json.replaceAll("\"token\"\\s*:\\s*\"(.*)\"", "\"token\":\"******\"");
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
            throw new CloudException(String.format("Problem occurred during certificates download for cluster with id %s, Rc stack trace is: %s", clusterId, getErrorString(e)));
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
