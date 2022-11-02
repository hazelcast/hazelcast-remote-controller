package com.hazelcast.remotecontroller;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.FirewallingNodeContext;
import com.hazelcast.instance.impl.HazelcastInstanceFactory;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.SplitBrainTestSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.hazelcast.instance.impl.HazelcastInstanceFactory.createInstanceName;

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
            throw new ServerException(e.getMessage());
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

        HazelcastInstance hzInstance = HazelcastInstanceFactory.newHazelcastInstance(config, createInstanceName(config), new FirewallingNodeContext());
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

    public boolean blockCommunicationBetween(String clusterId, String memberId, String otherMemberId) {
        LOG.warn("ClusterId " + clusterId + " MemberId " + memberId + " OtherMemberId " + otherMemberId);
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
        HazelcastInstance otherHazelcastInstance = hzCluster.getInstanceById(otherMemberId);
        if (otherHazelcastInstance == null) {
            LOG.info("Member does not exist: " + otherMemberId);
            return false;
        }
        SplitBrainTestSupport.blockCommunicationBetween(hazelcastInstance, otherHazelcastInstance);
        return true;
    }

    public boolean unblockCommunicationBetween(String clusterId, String memberId, String otherMemberId) {
        LOG.warn("ClusterId " + clusterId + " MemberId " + memberId + " OtherMemberId " + otherMemberId);
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
        HazelcastInstance otherHazelcastInstance = hzCluster.getInstanceById(otherMemberId);
        if (otherHazelcastInstance == null) {
            LOG.info("Member does not exist: " + otherMemberId);
            return false;
        }
        SplitBrainTestSupport.unblockCommunicationBetween(hazelcastInstance, otherHazelcastInstance);
        return true;
    }

    public boolean suspectMember(String clusterId, String memberId, String otherMemberId) {
        LOG.warn("ClusterId " + clusterId + " MemberId " + memberId + " OtherMemberId " + otherMemberId);
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
        HazelcastInstance otherHazelcastInstance = hzCluster.getInstanceById(otherMemberId);
        if (otherHazelcastInstance == null) {
            LOG.info("Member does not exist: " + otherMemberId);
            return false;
        }
        HazelcastTestSupport.suspectMember(hazelcastInstance, otherHazelcastInstance);
        return true;
    }

    public List<Long> getSchemasOnMember(String clusterId, String memberId) {
        try {
            HzCluster hzCluster = clusterMap.get(clusterId);
            if (hzCluster == null) {
                LOG.info("Cluster does not exist: " + clusterId);
                return null;
            }
            HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
            if (hazelcastInstance == null) {
                LOG.info("Member does not exist: " + memberId);
                return null;
            }
            // We will use reflection. If it fails, an error will be returned anyway.
            Node node = HazelcastTestSupport.getNode(hazelcastInstance);
            Method[] methods = node.getClass().getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals("getSchemaService")) {
                    Object schemaService = method.invoke(node);
                    Method[] methodsOfSchemaService = schemaService.getClass().getDeclaredMethods();
                    for (Method methodOfSchemaService : methodsOfSchemaService) {
                        if (methodOfSchemaService.getName().equals("getAllSchemas")) {
                            Collection<Object> schemas = (Collection<Object>) methodOfSchemaService.invoke(schemaService);
                            List<Long> schemaIds = new ArrayList<>();
                            for (Object schema : schemas) {
                                Method[] methodsOfSchema = schema.getClass().getDeclaredMethods();
                                for (Method methodOfSchema : methodsOfSchema) {
                                    if (methodOfSchema.getName().equals("getSchemaId")) {
                                        long schemaId = (long) methodOfSchema.invoke(schema);
                                        schemaIds.add(schemaId);
                                    }
                                }
                            }
                            return schemaIds;
                        }
                    }
                }
            }
            throw new RuntimeException(
                    "Remote controller tried to use reflection to get 'Node' of the HazelcastInstance and then tried to call " +
                            "node.getSchemaService().getAllSchemas() method to retrieve schemas. And then it tried to call getSchemaId() " +
                            "on each schema and then return the schema id list. This whole operation failed due to any of these not working. "
                            + "Please ensure the Hazelcast version running has these methods.");
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
