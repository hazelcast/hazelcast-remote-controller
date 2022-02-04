package com.hazelcast.remotecontroller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.lingala.zip4j.ZipFile;
import org.python.jline.internal.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class HazelcastCloudManager {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpRequest request;
    private String baseUrl;
    private URI uri;
    private final ObjectMapper mapper = new ObjectMapper();
    private String query;
    private JsonNode rootNode;
    private HttpResponse<String> response;
    private String bearerToken;
    private String clusterName;
    private static final Logger LOG = LogManager.getLogger(Main.class);
    private static int cidr = 99;

    public HazelcastCloudManager() {
    }

    public void login(String baseUrl, String apiKey, String apiSecret) {
        this.uri = URI.create(baseUrl + "/api/v1");
        this.baseUrl = baseUrl;
        bearerToken = getBearerToken(apiKey, apiSecret);
    }

    public CloudCluster createStandardCluster(String hazelcastVersion, boolean isTlsEnabled) {
        try {
            clusterName = "test-cluster-" + System.currentTimeMillis();
            query = String.format("{\"query\":\"mutation {createStarterCluster(input: {name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" clusterType: SMALL totalMemory: 2 hazelcastVersion: \\\"%s\\\" isTlsEnabled: %b } ) { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token} } }\"}",
                    clusterName,
                    "aws",
                    "us-west-2",
                    hazelcastVersion,
                    isTlsEnabled);

            Log.info(String.format("Request query: %s", query));
            response = createRequest(query);
            LOG.info(maskValueOfToken(response.body()));
            rootNode = mapper.readTree(response.body()).get("data").get("createStarterCluster");
            if(!waitForStateOfCluster(rootNode.get("id").asText(), "RUNNING", TimeUnit.MINUTES.toMillis(5)))
                throw new Exception("Wait for cluster state is not finished as expected");
            return getCluster(rootNode.get("id").asText());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean scaleUpDownStandardCluster(String clusterId, int scaleNumber) {
        int currentMemory;
        String requestUrl = String.format("%s/cluster/%s/updateMemory", baseUrl, clusterId);
        try {
            query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { specs {totalMemory} } }\" }", clusterId);
            response = createRequest(query);
            currentMemory = mapper.readTree(response.body()).get("data").get("cluster").get("specs").get("totalMemory").asInt();

            if (currentMemory + scaleNumber <= 0) {
                LOG.warn("Scaling number is not proper, there is no enough memory and nodes to scale down");
                return false;
            }

            String requestBody = String.format("{\"id\": %s, \"memory\": %d, \"autoScalingEnabled\": false}", clusterId, currentMemory + scaleNumber);
            request = HttpRequest.newBuilder()
                    .uri(new URI(requestUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info(mapper.readTree(response.body()).get("state"));
            waitForStateOfCluster(clusterId,"RUNNING", TimeUnit.MINUTES.toMillis(1));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public CloudCluster createEnterpriseCluster(String cloudProvider, String hazelcastVersion, boolean isTlsEnabled) {
        clusterName = "test-cluster-" + System.currentTimeMillis();
        String enterpriseClusterName = String.format("Enterprise-%s", clusterName);
        var provider = new CloudProviderDetails(cloudProvider);
        try {
            query = String.format("{ \"query\": \"mutation {createEnterpriseCluster(input:{name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" zones: [\\\"%s\\\"] zoneType: SINGLE instanceType: \\\"%s\\\" instancePerZone: 1 hazelcastVersion: \\\"%s\\\" isPublicAccessEnabled: true cidrBlock: \\\"%s\\\" isTlsEnabled: %b}){id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token}}}\" }",
                    enterpriseClusterName,
                    cloudProvider,
                    provider.region,
                    provider.zone,
                    provider.instanceType,
                    hazelcastVersion,
                    getProperCidr(),
                    isTlsEnabled
            );
            Log.info(String.format("Request query: %s", query));
            response = createRequest(query);
            LOG.info(maskValueOfToken(response.body()));
            rootNode = mapper.readTree(response.body()).get("data").get("createEnterpriseCluster");
            if(!waitForStateOfCluster(rootNode.get("id").asText(), "RUNNING", TimeUnit.MINUTES.toMillis(60)))
                throw new Exception("Wait for cluster state is not finished as expected");
            return getCluster(rootNode.get("id").asText());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CloudCluster getCluster(String clusterId) {
        try {
            query = String.format("{\"query\": \"query { cluster(clusterId: \\\"%s\\\") { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token}}}\"}", clusterId);
            response = createRequest(query);
            LOG.info(maskValueOfToken(response.body()));
            rootNode = mapper.readTree(response.body()).get("data").get("cluster");
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
            return null;
        }
    }

    public CloudCluster stopCluster(String clusterId) {
        try {
            query = String.format("{\"query\": \"mutation { stopCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            response = createRequest(query);
            LOG.info(maskValueOfToken(response.body()));
            if(waitForStateOfCluster(clusterId, "STOPPED", TimeUnit.MINUTES.toMillis(5)))
                return getCluster(clusterId);
            else
                throw new Exception("State cannot come to STOPPED");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CloudCluster resumeCluster(String clusterId) {
        try {
            query = String.format("{\"query\": \"mutation { resumeCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            response = createRequest(query);
            LOG.info(maskValueOfToken(response.body()));
            if(waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(5)))
                return getCluster(clusterId);
            else
                throw new Exception("State cannot come to RUNNING");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteCluster(String clusterId) {
        try {
            query = String.format("{\"query\": \"mutation { deleteCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            response = createRequest(query);
            LOG.info(response.body());
            waitForDeletedCluster(clusterId, TimeUnit.MINUTES.toMillis(10));
            LOG.info(String.format("Cluster with id %s is deleted", clusterId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    private HttpResponse<String> createRequest(String query) throws InterruptedException {
        int retryCountForExceptionOfEndpoint = 0;

        // Rarely server returns empty header and that is why a retry mechanism is added.
        while(retryCountForExceptionOfEndpoint < 3)
        {
            try {
                return client.send(HttpRequest.newBuilder()
                        .uri(uri)
                        .POST(HttpRequest.BodyPublishers.ofString(query))
                        .header("Content-Type", "application/json")
                        .header("Authorization", String.format("Bearer %s", bearerToken))
                        .build(), HttpResponse.BodyHandlers.ofString());
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
            request = HttpRequest.newBuilder()
                    .uri(new URI(requestUrl))
                    .GET()
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(response.body()).get("tlsPassword").asText();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private boolean waitForStateOfCluster(String clusterId, String expectedState, long timeoutInMillisecond) {
        String currentState = "";
        var startTime = System.currentTimeMillis();
        int retryCycle = 10;
        try {
            if(getClusterType(clusterId).equalsIgnoreCase("ENTERPRISE"))
                retryCycle = 120;

            while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
                query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { state } }\" }", clusterId);
                response = createRequest(query);
                LOG.info(response.body());
                currentState = mapper.readTree(response.body()).get("data").get("cluster").get("state").asText();
                if(currentState.equalsIgnoreCase(expectedState))
                    return true;
                if(currentState.equalsIgnoreCase("FAILED"))
                {
                    LOG.error("Cluster is FAILED");
                    return false;
                }
                TimeUnit.SECONDS.sleep(retryCycle);
            }
            LOG.error(String.format("The cluster cannot came to the given state in %d millisecond", timeoutInMillisecond));
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getClusterType(String clusterId)
    {
        try {
            query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { productType { name } } }\" }", clusterId);
            response = createRequest(query);
            return mapper.readTree(response.body()).get("data").get("cluster").get("productType").get("name").asText();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private void waitForDeletedCluster(String clusterId, long timeoutInMillisecond) throws Exception {
        var startTime = System.currentTimeMillis();
        int retryCycle = 10;
        if(getClusterType(clusterId).equalsIgnoreCase("ENTERPRISE"))
            retryCycle = 120;
        while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
            if (getCluster(clusterId) == null)
                return;
            TimeUnit.SECONDS.sleep(retryCycle);
        }
        throw new Exception(String.format("The cluster is not deleted in %d millisecond", timeoutInMillisecond));
    }

    private String getBearerToken(String apiKey, String apiSecret) {
        try {
            query = String.format("{ \"query\": \"mutation { login(apiKey: \\\"%s\\\", apiSecret: \\\"%s\\\") {token} }\" }", apiKey, apiSecret);
            request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .header("Content-Type", "application/json")
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(response.body()).get("data").get("login").get("token").asText();
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

    // In order to be able to create more than one enterprise cluster, CIDR blocks should be different.
    // When we need more than one cluster, I hope it won't generate the same IP :)
    private String getProperCidr() {
        Random r = new Random();
        cidr++;
        return String.format("10." + Integer.toString(cidr) + ".0.0/16");
    }

    // It creates a folder with name clusterId under /home/user/
    // Then it downloads the certificates for the cluster and put them in the created folder
    private String downloadCertificatesAndGetPath(String clusterId)
    {
        try
        {
            String userHome = System.getProperty("user.home");
            Path pathClusterId = Paths.get(userHome, clusterId);
            Path destination;

            // If folder with clusterId is there then certificates are already downloaded
            if(!Files.exists(pathClusterId))
            {
                new File(pathClusterId.toString()).mkdir();
                Path pathResponseZip = Paths.get(pathClusterId.toString(), "certificates.zip");

                destination = Paths.get(pathClusterId.toString(), "certificates");
                new File(destination.toString()).mkdir();

                String url = String.format("%s/cluster/%s/certificate", baseUrl, clusterId);
                var newRequest = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("Accept", "application/zip")
                        .header("Authorization", String.format("Bearer %s", bearerToken))
                        .GET()
                        .build();

                HttpResponse<byte[]> newResponse = client.send(newRequest, HttpResponse.BodyHandlers.ofByteArray());
                try (FileOutputStream stream = new FileOutputStream(pathResponseZip.toString())) {
                    stream.write(newResponse.body());
                }
                ZipFile zipFile = new ZipFile(pathResponseZip.toString());
                zipFile.extractAll(destination.toString());
                new File(pathResponseZip.toString()).delete();
            }
            return (Paths.get(pathClusterId.toString(), "certificates")).toString() + File.separator;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}

class CloudProviderDetails
{
    String region;
    String zone;
    String instanceType;

    public CloudProviderDetails(String providerName)
    {
        // These variables are hardcoded here but if we would like to use other regions, zones, instanceTypes we can set it them to env variable.
        // Not all regions have these kinds of instance types. Available ones are set.
        // There are some endpoints to decide regions and zones but as they are not so effective. First we need to get region then, zone then check instance type is available. If it is not check second region and so on.
        // That is why variables are set like that.

        switch (providerName)
        {
            case "aws":
                region = "us-west-2";
                zone = "us-west-2a";
                instanceType = "m5.large";
                break;
            case "azure":
                region = "westus2";
                zone = "1";
                instanceType = "Standard_E2_v3";
                break;
            case "gcp":
                region = "us-west2";
                zone = "us-west2-a";
                instanceType = "n1-highmem-4";
                break;
        }

        if(System.getenv("region") != null)
            region = System.getenv("region");
        if(System.getenv("zone") != null)
            zone = System.getenv("zone");
        if(System.getenv("instance_type") != null)
            instanceType = System.getenv("instance_type");

    }
}