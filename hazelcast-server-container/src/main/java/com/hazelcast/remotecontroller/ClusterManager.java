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

    public Cluster createCluster(String hzVersion, String xmlconfig) {
        HzCluster hzCluster = null;
        try {
            hzCluster = new HzCluster(hzVersion, xmlconfig);
        } catch (Exception e) {
            LOG.warn(e);
            return null;
        }
        this.clusterMap.putIfAbsent(hzCluster.getId(), hzCluster);
        return new Cluster(hzCluster.getId());
    }

    public Member startMember(String clusterId, int delay) {
        LOG.info("Starting a Member on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster != null) {
            Config config = hzCluster.getConfig();

            HazelcastInstance hzInstance = Hazelcast.newHazelcastInstance(config);
            com.hazelcast.core.Member member = hzInstance.getCluster().getLocalMember();
            if(hzCluster.addInstance(member.getUuid(), hzInstance)) {
                return new Member(member.getUuid(), member.getAddress().getHost(), member.getAddress().getPort());
            }
        }
        return null;
    }

    public boolean shutdownMember(String clusterId, String memberId, int delay) {
        LOG.info("Shutting down the Member "+ memberId + "on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        hazelcastInstance.shutdown();
        hzCluster.removeInstance(memberId);
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
