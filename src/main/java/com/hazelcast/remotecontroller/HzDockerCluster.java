package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HzDockerCluster {

    private static Logger LOG = LogManager.getLogger(Main.class);
    private final String clusterId = UUID.randomUUID().toString();
    private final String dockerImageString;
    private final String xmlConfigPath;
    private final Network network;
    private final ConcurrentHashMap<String, GenericContainer> containers = new ConcurrentHashMap<>();
    // Holds the firewall rules that is applied via iptables to split the brain. The key is the container id and value is the
    // list of blocked ips for input for that container
    private final Map<String, List<String>> blockRulesOfContainers;

    public HzDockerCluster(String dockerImageString, String xmlConfigPath) {
        this.dockerImageString = dockerImageString;
        this.xmlConfigPath = xmlConfigPath;
        this.network = Network.newNetwork();
        this.blockRulesOfContainers = new HashMap<>();
    }

    public String getClusterId() {
        return clusterId;
    }

    public void addContainer(String containerId, GenericContainer container) {
        this.containers.put(containerId, container);
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
        try {
            container.start();
        } catch (Exception e) {
            LOG.error("Could not start container: ", e);
            throw e;
        }
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
            LOG.warn("Container does not exist with id: " + containerId);
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
            splitContainersAsBrains(brain1, brain2);
        } catch (Exception e) {
            LOG.error("Could not split brains", e);
            return false;
        }
        return true;
    }

    private void splitContainersAsBrains(List<DockerMember> brain1, List<DockerMember> brain2) throws IOException, InterruptedException {
        for (DockerMember member : brain1) {
            GenericContainer container = this.containers.get(member.getContainerId());
            blockInputFromContainers(container, brain2);
        }
        for (DockerMember member : brain2) {
            GenericContainer container = this.containers.get(member.getContainerId());
            blockInputFromContainers(container, brain1);
        }
    }

    public boolean mergeBrains() {
        try {
            for (Map.Entry<String, List<String>> entry : this.blockRulesOfContainers.entrySet()) {
                String containerId = entry.getKey();
                List<String> blockedIps = entry.getValue();
                unblockInputFromContainers(containerId, blockedIps);
            }
            return true;
        } catch (Exception e) {
            LOG.error("Could not merge brains", e);
            return false;
        }
    }

    public void shutdown() throws IOException {
        Iterator<GenericContainer> iterator = this.containers.values().iterator();
        while (iterator.hasNext()) {
            GenericContainer container = iterator.next();
            String str = container.getLogs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(container.getContainerId() + ".logs", true));
            writer.append(str);
            writer.close();
            container.stop();
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

    private void blockInputFromContainers(GenericContainer containerToBeAffected, List<DockerMember> containers) throws IOException, InterruptedException {
        for (DockerMember member : containers) {
            containerToBeAffected.execInContainer(String.format("sudo iptables -A INPUT -s %s -j DROP", member.getHost()));
            addToBlockRule(containerToBeAffected.getContainerId(), member.getHost());
        }
    }

    private void addToBlockRule(String containerId, String blockedIp) {
        if (blockRulesOfContainers.containsKey(containerId)) {
            blockRulesOfContainers.get(containerId).add(blockedIp);
        } else {
            List<String> blockedIps = new ArrayList<>();
            blockedIps.add(blockedIp);
            blockRulesOfContainers.put(containerId, blockedIps);
        }
    }

    private void unblockInputFromContainers(String containerId, List<String> blockedIps) throws IOException, InterruptedException {
        GenericContainer containerToBeAffected = this.containers.get(containerId);
        for (String ip : blockedIps) {
            containerToBeAffected.execInContainer(String.format("sudo iptables -D INPUT -s %s -j DROP", ip));
        }
    }
}
