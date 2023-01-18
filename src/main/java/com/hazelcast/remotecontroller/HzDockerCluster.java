package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HzDockerCluster {

    private static Logger LOG = LogManager.getLogger(Main.class);
    private final String clusterId = UUID.randomUUID().toString();
    private final String dockerImageString;
    private final String xmlConfigPath;
    private final Network network;
    private final ConcurrentHashMap<String, GenericContainer> containers = new ConcurrentHashMap<>();

    public HzDockerCluster(String dockerImageString, String xmlConfigPath) {
        this.dockerImageString = dockerImageString;
        this.xmlConfigPath = xmlConfigPath;
        this.network = Network.newNetwork();
    }

    public String getClusterId() {
        return clusterId;
    }

    public void addContainer(String containerId, GenericContainer container) {
        this.containers.putIfAbsent(containerId, container);
    }

    public DockerMember createDockerMember() {
        GenericContainer container;
        if (xmlConfigPath != null) {
            MountableFile mountableFile = MountableFile.forHostPath(xmlConfigPath);
            // This port is from the container's point of view, actual port on the host is mapped by testcontainers randomly
            container = new GenericContainer(DockerImageName.parse(dockerImageString))
                    .withEnv("JAVA_OPTS", "-Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml")
                    .withEnv("HZ_PHONE_HOME_ENABLED", "false")
                    .withCopyFileToContainer(mountableFile, "/opt/hazelcast/config_ext/hazelcast.xml")
                    .withNetwork(network)
                    .withExposedPorts(5701);
        } else {
            container = new GenericContainer(DockerImageName.parse(dockerImageString))
                    .withEnv("HZ_PHONE_HOME_ENABLED", "false")
                    .withNetwork(network)
                    .withExposedPorts(5701);
        }
        container.start();
        if (!container.isRunning()) {
            throw new RuntimeException("Container could not be started");
        }

        Integer port = container.getMappedPort(5701);
        String host = container.getHost();

        String containerId = UUID.randomUUID().toString();
        this.addContainer(containerId, container);
        return new DockerMember(containerId, host, port);
    }

    public boolean stopAndRemoveContainerById(String containerId) {
        GenericContainer container = this.containers.get(containerId);
        if (container == null) {
            LOG.info("Container does not exist with id: " + containerId);
            return false;
        }

        container.stop();
        this.containers.remove(containerId);
        return true;
    }

    public boolean splitClusterAs(List<DockerMember> brain1, List<DockerMember> brain2) {
        boolean allMembersAreInCluster;
        allMembersAreInCluster = checkMembersAreInThisCluster(brain1);
        if (!allMembersAreInCluster) {
            return false;
        }
        allMembersAreInCluster = checkMembersAreInThisCluster(brain2);
        if (!allMembersAreInCluster) {
            return false;
        }
        try {
            disconnectAllContainersFromClusterNetwork();
        } catch (Exception e) {
            LOG.error("Could not disconnect all containers from the cluster network during split brain attempt", e);
            return false;
        }
        try {
            Network brain1Network = Network.newNetwork();
            Network brain2Network = Network.newNetwork();

            for (DockerMember member : brain1) {
                GenericContainer container = this.containers.get(member.getContainerId());
                connectContainerToNetwork(container.getContainerId(), brain1Network.getId());
            }

            for (DockerMember member : brain2) {
                GenericContainer container = this.containers.get(member.getContainerId());
                connectContainerToNetwork(container.getContainerId(), brain2Network.getId());
            }
        } catch (Exception e) {
            LOG.error("Could not connect containers to the brain networks during split brain attempt", e);
            return false;
        }

        return true;
    }

    public boolean mergeCluster() {
        try {
            disconnectAllContainersFromTheirNetworks();
        } catch (Exception e) {
            LOG.error("Could not disconnect all containers from their network during merge cluster", e);
            return false;
        }
        try {
            connectAllContainersToClusterNetwork();
        } catch (Exception e) {
            LOG.error("Could not connect containers to the cluster network during merge cluster", e);
            return false;
        }
        return true;
    }

    public void shutdown() {
        Iterator<GenericContainer> iterator = this.containers.values().iterator();
        if (iterator.hasNext()) {
            iterator.next().stop();
        }
        this.containers.clear();
    }

    private boolean checkMembersAreInThisCluster(List<DockerMember> members) {
        for (DockerMember member : members) {
            if (!this.containers.containsKey(member.getContainerId())) {
                LOG.warn("Member " + member + " is not in this docker cluster");
                return false;
            }
        }
        return true;
    }

    private void disconnectAllContainersFromClusterNetwork() {
        for (GenericContainer container : this.containers.values()) {
            disconnectContainerFromNetwork(container.getContainerId(), this.network.getId(), true);
        }
    }

    private void connectAllContainersToClusterNetwork() {
        for (GenericContainer container : this.containers.values()) {
            connectContainerToNetwork(container.getContainerId(), this.network.getId());
        }
    }

    private void disconnectAllContainersFromTheirNetworks() {
        for (GenericContainer container : this.containers.values()) {
            Network network = container.getNetwork();
            if (network == null) {
                throw new RuntimeException("Container " + container.getContainerId() + " is not connected to a network");
            }
            disconnectContainerFromNetwork(container.getContainerId(), network.getId(), true);
        }
    }

    private void disconnectContainerFromNetwork(String containerId, String networkId, boolean force) {
        DockerClientFactory.instance().client().disconnectFromNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .withForce(force)
                .exec();
    }

    private void connectContainerToNetwork(String containerId, String networkId) {
        DockerClientFactory.instance().client().connectToNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .exec();
    }
}
