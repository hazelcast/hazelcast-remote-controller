package com.hazelcast.remotecontroller;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class ClusterManager {

    private static Logger LOG = LogManager.getLogger(Main.class);

    private final ConcurrentHashMap<String, HzCluster> clusterMap = new ConcurrentHashMap<>();

    public ClusterManager() {
    }

    public Cluster createCluster(String hzVersion, String xmlconfig, boolean keepClusterName) throws ServerException {
        try {
            HzCluster hzCluster = new HzCluster(hzVersion, xmlconfig, keepClusterName);
            final HzCluster existingCluster = this.clusterMap.putIfAbsent(hzCluster.getId(), hzCluster);
            if (existingCluster != null) {
                if (!existingCluster.getXmlConfig().equals(xmlconfig)) {
                    throw new ServerException("A cluster with the same cluster-name " + hzCluster.getId()
                            + " but with a different config already exists. Existing cluster config: " + hzCluster.getXmlConfig()
                            + "\n" + ", new config:" + xmlconfig);
                }
            }
            return new Cluster(hzCluster.getId());
        } catch (Exception e) {
            LOG.warn(e);
            throw new ServerException(e.toString());
        }
    }

    public HzCluster getCluster(String clusterId) {
        return clusterMap.get(clusterId);
    }

    public Member startMember(String clusterId) throws ServerException {
        LOG.info("Starting a Member on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            String log = "Cannot find Cluster with id:" + clusterId;
            LOG.info(log);
            throw new ServerException(log);
        }
        Config config = hzCluster.getConfig();

        LOG.info(config.getNetworkConfig().getJoin().getTcpIpConfig());

        HazelcastInstance hzInstance = Hazelcast.newHazelcastInstance(config);
        com.hazelcast.cluster.Member member = hzInstance.getCluster().getLocalMember();
        hzCluster.addInstance(member.getUuid().toString(), hzInstance);
        return new Member(member.getUuid().toString(), member.getAddress().getHost(), member.getAddress().getPort());
    }

    public boolean shutdownMember(String clusterId, String memberId) {
        LOG.info("Shutting down the Member " + memberId + "on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        if (hazelcastInstance == null) {
            LOG.info("Member does not exist: " + memberId);
            return false;
        }
        hazelcastInstance.shutdown();
        hzCluster.removeInstance(memberId);
        return true;
    }

    public boolean terminateMember(String clusterId, String memberId) {
        LOG.info("Terminating the Member " + memberId + "on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        if (hazelcastInstance == null) {
            LOG.info("Member does not exist: " + memberId);
            return false;
        }
        hazelcastInstance.getLifecycleService().terminate();
        hzCluster.removeInstance(memberId);
        return true;
    }

    public boolean suspendMember(String clusterId, String memberId) {
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        if (hazelcastInstance == null) {
            LOG.info("Member does not exist: " + memberId);
            return false;
        }
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains(hazelcastInstance.getName())) {
                thread.suspend();
            }
        }
        return true;
    }

    public boolean resumeMember(String clusterId, String memberId) {
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        if (hazelcastInstance == null) {
            LOG.info("Member does not exist: " + memberId);
            return false;
        }
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains(hazelcastInstance.getName())) {
                thread.resume();
            }
        }
        return true;
    }

    public boolean shutdownCluster(String clusterId) {
        HzCluster hzCluster = clusterMap.get(clusterId);
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

    public boolean terminateCluster(String clusterId) {
        LOG.info("Terminating the cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster == null) {
            LOG.info("Cluster does not exist: " + clusterId);
            return false;
        }
        try {
            hzCluster.terminate();
        } catch (Exception e) {
            LOG.info("Exception during cluster terminate: ", e);
        }
        this.clusterMap.remove(hzCluster.getId());
        return true;
    }


}
