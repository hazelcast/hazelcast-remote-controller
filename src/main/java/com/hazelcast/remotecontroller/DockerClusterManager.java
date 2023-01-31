package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DockerClusterManager {

    private static final Logger LOG = LogManager.getLogger(Main.class);
    private final ConcurrentHashMap<String, HzDockerCluster> clusterMap = new ConcurrentHashMap<>();

    public DockerCluster createCluster(String dockerImageString, String xmlconfig, String hazelcastEnterpriseLicenseKey) throws ServerException {
        try {
            HzDockerCluster hzDockerCluster = new HzDockerCluster(dockerImageString, xmlconfig, hazelcastEnterpriseLicenseKey);
            this.clusterMap.put(hzDockerCluster.getClusterId(), hzDockerCluster);
            return new DockerCluster(hzDockerCluster.getClusterId());
        } catch (Exception e) {
            LOG.error(e);
            throw new ServerException(e.getMessage());
        }
    }

    public DockerMember startMember(String clusterId) throws ServerException {
        LOG.info("Starting a container on docker cluster with cluster id: " + clusterId);
        HzDockerCluster hzDockerCluster = clusterMap.get(clusterId);
        if (hzDockerCluster == null) {
            String log = "Cannot find docker cluster with id:" + clusterId;
            LOG.error(log);
            throw new ServerException(log);
        }
        try {
            return hzDockerCluster.createDockerMember();
        } catch (Exception e) {
            LOG.error(e);
            throw new ServerException(e.getMessage());
        }
    }

    public boolean shutdownMember(String dockerClusterId, String containerId) {
        LOG.info("Shutting down the member with container id " + containerId + " on docker cluster : " + dockerClusterId);
        HzDockerCluster hzDockerCluster = this.clusterMap.get(dockerClusterId);
        if (hzDockerCluster == null) {
            LOG.warn("Docker cluster does not exist with id: " + dockerClusterId);
            return false;
        }
        return hzDockerCluster.stopAndRemoveContainerById(containerId);
    }

    public boolean shutdownCluster(String clusterId) {
        HzDockerCluster hzCluster = this.clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.warn("Docker cluster does not exist with id: " + clusterId);
            return false;
        }
        LOG.info("Shutting down the docker cluster : " + clusterId);
        try {
            hzCluster.shutdown();
        } catch (Exception e) {
            LOG.error("Exception during cluster shutdown: ", e);
            return false;
        }
        this.clusterMap.remove(hzCluster.getClusterId());
        return true;
    }

    public boolean splitClusterAs(String dockerClusterId, List<DockerMember> brain1, List<DockerMember> brain2) {
        HzDockerCluster hzCluster = this.clusterMap.get(dockerClusterId);
        if (hzCluster == null) {
            LOG.warn("Docker cluster does not exist with id: " + dockerClusterId);
            return false;
        }
        LOG.info("Splitting the docker cluster : " + dockerClusterId);
        return hzCluster.splitClusterAs(brain1, brain2);
    }

    public boolean mergeCluster(String dockerClusterId) {
        HzDockerCluster hzCluster = this.clusterMap.get(dockerClusterId);
        if (hzCluster == null) {
            LOG.warn("Docker cluster does not exist with id: " + dockerClusterId);
            return false;
        }

        LOG.info("Merging the docker cluster : " + dockerClusterId);
        return hzCluster.mergeBrains();
    }
}
