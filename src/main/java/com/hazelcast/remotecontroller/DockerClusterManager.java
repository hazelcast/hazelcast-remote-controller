package com.hazelcast.remotecontroller;

import com.hazelcast.core.Hazelcast;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DockerClusterManager {

    private static Logger LOG = LogManager.getLogger(Main.class);
    private final ConcurrentHashMap<String, HzDockerCluster> clusterMap = new ConcurrentHashMap<>();
    private final Network network;

    public DockerClusterManager() {
        this.network = Network.newNetwork();
    }

    public DockerCluster createCluster(String dockerImageString, String xmlconfig) throws ServerException {
        try {
            HzDockerCluster hzDockerCluster = new HzDockerCluster(dockerImageString, xmlconfig);
            final HzDockerCluster existingCluster = this.clusterMap.putIfAbsent(hzDockerCluster.getId(), hzDockerCluster);
            if (existingCluster != null) {
                if (!existingCluster.getXmlConfigPath().equals(xmlconfig)) {
                    throw new ServerException("A docker cluster with the same cluster-name " + hzDockerCluster.getId()
                            + " but with a different config already exists. Existing cluster config: " + hzDockerCluster.getXmlConfigPath()
                            + "\n" + ", new config:" + xmlconfig);
                }
            }
            return new DockerCluster(hzDockerCluster.getId());
        } catch (Exception e) {
            LOG.warn(e);
            throw new ServerException(e.getMessage());
        }
    }

    public DockerMember startMember(String clusterId) throws ServerException {

        LOG.info("Starting a DockerMember on cluster : " + clusterId);
        HzDockerCluster hzDockerCluster = clusterMap.get(clusterId);
        if (hzDockerCluster == null) {
            String log = "Cannot find Cluster with id:" + clusterId;
            LOG.info(log);
            throw new ServerException(log);
        }
        String dockerImageString = hzDockerCluster.getDockerImageString();

        MountableFile mountableFile = MountableFile.forHostPath(hzDockerCluster.getXmlConfigPath());

        // This port is from the container's point of view, actual port on the host is mapped by testcontainers randomly
        GenericContainer container = new GenericContainer(DockerImageName.parse(dockerImageString))
                .withEnv("JAVA_OPTS", "-Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml")
                .withEnv("HZ_PHONE_HOME_ENABLED", "false")
                .withCopyFileToContainer(mountableFile, "/opt/hazelcast/config_ext/hazelcast.xml")
                .withNetwork(this.network)
                .withExposedPorts(5701);
        Integer port = container.getMappedPort(5701);
        String host = container.getHost();


        String containerId = UUID.randomUUID().toString();
        hzDockerCluster.addInstance(containerId, container);
        return new DockerMember(containerId, host, port);
    }

    public boolean shutdownMember(String clusterId, String memberId) {
        LOG.info("Shutting down the DockerMember " + memberId + "on cluster : " + clusterId);
        HzDockerCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        GenericContainer container = hzCluster.getContainerById(memberId);
        if (container == null) {
            LOG.info("DockerMember does not exist: " + memberId);
            return false;
        }
        container.close();
        hzCluster.removeContainer(memberId);
        return true;
    }

    public boolean shutdownCluster(String clusterId) {
        HzDockerCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        LOG.info("Shutting down the cluster : " + clusterId);
        try {
            hzCluster.shutdown();
        } catch (Exception e) {
            LOG.info("Exception during cluster shutdown: ", e);
            return false;
        }
        this.clusterMap.remove(hzCluster.getId());
        return true;
    }

    public boolean clean() {
        LOG.info("Cleaning the R.C. Cluster Manager");
        shutdownAll();
        clusterMap.clear();
        return true;
    }

    public boolean ping() {
        LOG.info("Ping ... Pong ...");
        return true;
    }

    public boolean shutdownAll() {
        LOG.info("Shutting down the cluster.");
        Hazelcast.shutdownAll();
        return true;
    }
}
