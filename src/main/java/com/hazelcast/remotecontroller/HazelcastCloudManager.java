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

    public HazelcastCloudManager() {
        if(System.getenv("baseUrl") != null && System.getenv("apiKey") != null && System.getenv("apiSecret") != null)
            loginToHazelcastCloud(System.getenv("baseUrl"), System.getenv("apiKey"), System.getenv("apiSecret"));
        else
            LOG.info("Hazelcast cloud credentials are not set as env variable, please call login method first or cloud API cannot be used with RC");
    }

    public void loginToHazelcastCloud(String baseUrl, String apiKey, String apiSecret) {
        this.uri = URI.create(baseUrl + "/api/v1");
        this.baseUrl = baseUrl;
        bearerToken = getBearerToken(apiKey, apiSecret);
        if(bearerToken == null)
            LOG.error("Login failed");
    }

    public CloudCluster createHazelcastCloudStandardCluster(String hazelcastVersion, boolean isTlsEnabled) {
        try {
            String clusterName = "test-cluster-" + System.currentTimeMillis();
            String query = String.format("{\"query\":\"mutation {createStarterCluster(input: {name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" clusterType: SMALL totalMemory: 2 hazelcastVersion: \\\"%s\\\" isTlsEnabled: %b } ) { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token} } }\"}",
                    clusterName,
                    "aws",
                    "us-west-2",
                    hazelcastVersion,
                    isTlsEnabled);

            Log.info(String.format("Request query: %s", query));
            String responseBody = createRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            JsonNode rootNode = mapper.readTree(responseBody).get("data").get("createStarterCluster");
            if(!waitForStateOfCluster(rootNode.get("id").asText(), "RUNNING", TimeUnit.MINUTES.toMillis(5)))
                throw new Exception("Wait for cluster state is not finished as expected");
            return getHazelcastCloudCluster(rootNode.get("id").asText());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean scaleUpDownHazelcastCloudStandardCluster(String clusterId, int scaleNumber) {
        int currentMemory;
        String requestUrl = String.format("%s/cluster/%s/updateMemory", baseUrl, clusterId);
        try {
            String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { specs {totalMemory} } }\" }", clusterId);
            String responseBody = createRequest(query).body().string();
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
            waitForStateOfCluster(clusterId,"RUNNING", TimeUnit.MINUTES.toMillis(1));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public CloudCluster createHazelcastCloudEnterpriseCluster(String cloudProvider, String hazelcastVersion, boolean isTlsEnabled) {
        String clusterName = "test-cluster-" + System.currentTimeMillis();
        String enterpriseClusterName = String.format("Enterprise-%s", clusterName);
        CloudProviderDetails provider = new CloudProviderDetails(cloudProvider);
        try {
            int retryCount = 0;
            String responseBody = null;
            while (retryCount < 5) {
                String cidr = getProperCidr();
                String query = String.format("{ \"query\": \"mutation {createEnterpriseCluster(input:{name: \\\"%s\\\" cloudProvider: \\\"%s\\\" region: \\\"%s\\\" zones: [\\\"%s\\\"] zoneType: SINGLE instanceType: \\\"%s\\\" instancePerZone: 1 hazelcastVersion: \\\"%s\\\" isPublicAccessEnabled: true cidrBlock: \\\"%s\\\" isTlsEnabled: %b}){id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token}}}\" }",
                        enterpriseClusterName,
                        cloudProvider,
                        provider.region,
                        provider.zone,
                        provider.instanceType,
                        hazelcastVersion,
                        cidr,
                        isTlsEnabled
                );
                Log.info(String.format("Request query: %s", query));
                responseBody = createRequest(query).body().string();
                if(!responseBody.contains(String.format("%s is already in use", cidr)))
                    break;
                retryCount++;
                TimeUnit.SECONDS.sleep(30);
            }
            LOG.info(maskValueOfToken(responseBody));
            JsonNode rootNode = mapper.readTree(responseBody).get("data").get("createEnterpriseCluster");
            if(!waitForStateOfCluster(rootNode.get("id").asText(), "RUNNING", TimeUnit.MINUTES.toMillis(60)))
                throw new Exception("Wait for cluster state is not finished as expected");
            return getHazelcastCloudCluster(rootNode.get("id").asText());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CloudCluster getHazelcastCloudCluster(String clusterId) {
        try {
            String query = String.format("{\"query\": \"query { cluster(clusterId: \\\"%s\\\") { id name hazelcastVersion isTlsEnabled state discoveryTokens {source,token}}}\"}", clusterId);
            String responseBody = createRequest(query).body().string();
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
            return null;
        }
    }

    public CloudCluster stopHazelcastCloudCluster(String clusterId) {
        try {
            String query = String.format("{\"query\": \"mutation { stopCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = createRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            if(waitForStateOfCluster(clusterId, "STOPPED", TimeUnit.MINUTES.toMillis(5)))
                return getHazelcastCloudCluster(clusterId);
            else
                throw new Exception("State cannot come to STOPPED");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CloudCluster resumeHazelcastCloudCluster(String clusterId) {
        try {
            String query = String.format("{\"query\": \"mutation { resumeCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = createRequest(query).body().string();
            LOG.info(maskValueOfToken(responseBody));
            if(waitForStateOfCluster(clusterId, "RUNNING", TimeUnit.MINUTES.toMillis(5)))
                return getHazelcastCloudCluster(clusterId);
            else
                throw new Exception("State cannot come to RUNNING");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteHazelcastCloudCluster(String clusterId) {
        try {
            String query = String.format("{\"query\": \"mutation { deleteCluster(clusterId:\\\"%s\\\") { clusterId }}\"}", clusterId);
            String responseBody = createRequest(query).body().string();
            LOG.info(responseBody);
            waitForDeletedCluster(clusterId, TimeUnit.MINUTES.toMillis(10));
            LOG.info(String.format("Cluster with id %s is deleted", clusterId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    private Response createRequest(String query) throws InterruptedException {
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
            if(getClusterType(clusterId).equalsIgnoreCase("ENTERPRISE"))
                retryCycle = 120;

            while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
                String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { id state } }\" }", clusterId);
                String responseBody = createRequest(query).body().string();
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
            String query = String.format("{ \"query\": \"query { cluster(clusterId: \\\"%s\\\") { productType { name } } }\" }", clusterId);
            Response response = createRequest(query);
            return mapper.readTree(response.body().string()).get("data").get("cluster").get("productType").get("name").asText();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private void waitForDeletedCluster(String clusterId, long timeoutInMillisecond) throws Exception {
        long startTime = System.currentTimeMillis();
        int retryCycle = 10;
        if(getClusterType(clusterId).equalsIgnoreCase("ENTERPRISE"))
            retryCycle = 120;
        while ((System.currentTimeMillis() - startTime) < timeoutInMillisecond) {
            if (getHazelcastCloudCluster(clusterId) == null)
                return;
            TimeUnit.SECONDS.sleep(retryCycle);
        }
        throw new Exception(String.format("The cluster is not deleted in %d millisecond", timeoutInMillisecond));
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

    // In order to be able to create more than one enterprise cluster, CIDR blocks should be different.
    // This endpoint provides unique CIDR
    private String getProperCidr() {
        String requestUrl = String.format("%s/dedicated_clusters/available_vpc_cidr", baseUrl);
        try {
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .get()
                    .header("Authorization", String.format("Bearer %s", bearerToken))
                    .build();
            call = client.newCall(request);
            Response response = call.execute();
            try {
                return mapper.readTree(response.body().string()).get("vpcCidr").asText();
            } catch (IOException e) {
                LOG.warn("Body is null for vpcCidr");
                return null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
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