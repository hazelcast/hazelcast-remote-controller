package com.hazelcast.remotecontroller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ContainerNetwork;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.TestEnvironment;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class HzDockerCluster {
    private static class ExecResult {
        public final Long exitCode;
        public final String stdout;
        public final String stderr;

        public ExecResult(Long exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccessful() {
            return exitCode == 0L;
        }
    }

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
                    // For iptables to work, (at least for Mac) we need to run the container in privileged mode
                    .withPrivilegedMode(true)
                    .withExposedPorts(5701);
        } else {
            container = new GenericContainer(DockerImageName.parse(dockerImageString))
                    .withEnv("HZ_PHONE_HOME_ENABLED", "false")
                    .withNetwork(network)
                    // For iptables to work, (at least for Mac) we need to run the container in privileged mode
                    .withPrivilegedMode(true)
                    .withExposedPorts(5701);
        }
        container.start();
        if (!container.isRunning()) {
            throw new RuntimeException("Container could not be started");
        }

        Integer port = container.getMappedPort(5701);
        String host = container.getHost();
        String containerId = container.getContainerId();

        this.addContainer(container.getContainerId(), container);
        tryInstallingIptables(container);
        return new DockerMember(containerId, host, port);
    }

    private static void tryInstallingIptables(GenericContainer container) {
        LOG.info("Installing iptables in the container.");
        boolean installed = tryInstallingIptablesWithApk(container);
        if (!installed) {
            boolean installedWithApt = tryInstallingIptablesWithAptGet(container);
            if(!installedWithApt) {
                throw new RuntimeException("Could not install iptables in the container.");
            }
        }
    }

    private static boolean tryInstallingIptablesWithApk(GenericContainer container) {
        try {
            execAsRootAndThrowOnError(container, "apk", "update");
            execAsRootAndThrowOnError(container, "apk", "add", "iptables");
            return true;
        } catch (Throwable e) {
            LOG.error(e);
            return false;
        }
    }

    private static boolean tryInstallingIptablesWithAptGet(GenericContainer container) {
        try {
            execAsRootAndThrowOnError(container, "apt-get", "update");
            execAsRootAndThrowOnError(container, "apt-get", "install", "iptables");
            return true;
        } catch (Throwable e) {
            return false;
        }
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

    private void blockInputFromContainers(GenericContainer containerToBeAffected, List<DockerMember> dockerMembers) throws IOException, InterruptedException {
        for (DockerMember member : dockerMembers) {
            GenericContainer container = this.containers.get(member.getContainerId());
            String containerIpAddress = getContainerIpAddress(container);
            execAsRootAndThrowOnError(containerToBeAffected, "iptables", "-A", "INPUT", "-s", containerIpAddress, "-j", "DROP");
            addToBlockRule(containerToBeAffected.getContainerId(), containerIpAddress);
        }
    }

    private static String getContainerIpAddress(GenericContainer container) {
        Collection<ContainerNetwork> networks = container.getContainerInfo().getNetworkSettings().getNetworks().values();
        if (networks.isEmpty()) {
            throw new RuntimeException("Container " + container.getContainerId() + " is not connected to any network."
            + " In order to split brain the container, it has to be connected to a network.");
        }
        ContainerNetwork network = networks.iterator().next();
        return network.getIpAddress();
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
        GenericContainer container = this.containers.get(containerId);
        for (String ip : blockedIps) {
            execAsRootAndThrowOnError(container, "iptables", "-D", "INPUT", "-s", ip, "-j", "DROP");
        }
    }

    private static void execAsRootAndThrowOnError(GenericContainer container, String... cmd) throws IOException, InterruptedException {
        ExecResult execResult = execInContainerAsRoot(container, cmd);
        Long exitCode = execResult.exitCode;
        String stdout = execResult.stdout;
        String stderr = execResult.stderr;
        if (!execResult.isSuccessful()) {
            String error = String.format("Could not execute command: %s in container with id: %s \n", String.join(" ", cmd), container.getContainerId()) +
                    String.format("Exit code: %d, Stdout: %s, Stderr: %s", exitCode, stdout, stderr);
            throw new RuntimeException(error);
        }
    }

    private static ExecResult execInContainerAsRoot(GenericContainer container, String... cmd) throws IOException, InterruptedException {
        DockerClient dockerClient = container.getDockerClient();
        InspectContainerResponse containerInfo = container.getContainerInfo();
        if (!TestEnvironment.dockerExecutionDriverSupportsExec()) {
            throw new UnsupportedOperationException("Your docker daemon is running the \"lxc\" driver, which doesn't support \"docker exec\".");
        } else if (!isRunning(containerInfo)) {
            throw new IllegalStateException("execInContainer can only be used while the Container is running");
        } else {
            String containerId = containerInfo.getId();
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(cmd)
                    .withUser("root")
                    .exec();
            ToStringConsumer stdoutConsumer = new ToStringConsumer();
            ToStringConsumer stderrConsumer = new ToStringConsumer();
            try (FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
                callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer);
                callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer);
                dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion();
            }
            Long exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().getExitCodeLong();
            String stdout = stdoutConsumer.toString(StandardCharsets.UTF_8);
            String stderr = stderrConsumer.toString(StandardCharsets.UTF_8);
            return new ExecResult(exitCode, stdout, stderr);
        }
    }

    private static boolean isRunning(InspectContainerResponse containerInfo) {
        try {
            return containerInfo != null && containerInfo.getState().getRunning();
        } catch (DockerException var2) {
            return false;
        }
    }
}
