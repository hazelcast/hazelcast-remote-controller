package com.hazelcast.remotecontroller;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.management.ScriptEngineManagerContext;
import org.apache.thrift.TException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.List;
import java.util.Locale;

public class RemoteControllerHandler implements RemoteController.Iface {

    private final ClusterManager clusterManager;
    private final DockerClusterManager dockerClusterManager;
    private HazelcastCloudManager cloudManager;

    public RemoteControllerHandler() {
        this.clusterManager = new ClusterManager();
        this.dockerClusterManager = new DockerClusterManager();
    }

    @Override
    public boolean ping() throws TException {
        return clusterManager.ping();
    }

    @Override
    public boolean clean() throws TException {
        return clusterManager.clean();
    }

    @Override
    public boolean exit() throws TException {
        return clusterManager.clean();
    }

    @Override
    public Cluster createCluster(String hzVersion, String xmlconfig) throws TException {
        return clusterManager.createCluster(hzVersion, xmlconfig, false);
    }

    @Override
    public Cluster createClusterKeepClusterName(String hzVersion, String xmlconfig) throws TException {
        return clusterManager.createCluster(hzVersion, xmlconfig, true);
    }

    @Override
    public boolean shutdownCluster(String clusterId) throws TException {
        return clusterManager.shutdownCluster(clusterId);
    }

    @Override
    public boolean terminateCluster(String clusterId) throws TException {
        return clusterManager.terminateCluster(clusterId);
    }

    @Override
    public Member startMember(String clusterId) throws TException {
        return clusterManager.startMember(clusterId);
    }

    @Override
    public boolean shutdownMember(String clusterId, String memberId) throws TException {
        return clusterManager.shutdownMember(clusterId, memberId);
    }

    @Override
    public boolean terminateMember(String clusterId, String memberId) throws TException {
        return clusterManager.terminateMember(clusterId, memberId);
    }

    @Override
    public boolean suspendMember(String clusterId, String memberId) throws TException {
        return clusterManager.suspendMember(clusterId, memberId);
    }

    @Override
    public boolean resumeMember(String clusterId, String memberId) throws TException {
        return clusterManager.resumeMember(clusterId, memberId);
    }

    @Override
    public DockerCluster createDockerCluster(String dockerImageString, String xmlconfig, String hazelcastEnterpriseLicenseKey) throws TException {
        return dockerClusterManager.createCluster(dockerImageString, xmlconfig, hazelcastEnterpriseLicenseKey);
    }

    @Override
    public boolean shutdownDockerCluster(String dockerClusterId) throws TException {
        return dockerClusterManager.shutdownCluster(dockerClusterId);
    }

    @Override
    public DockerMember startDockerMember(String dockerClusterId) throws TException {
        return dockerClusterManager.startMember(dockerClusterId);
    }

    @Override
    public boolean shutdownDockerMember(String dockerClusterId, String dockerMemberId) throws TException {
        return dockerClusterManager.shutdownMember(dockerClusterId, dockerMemberId);
    }

    @Override
    public boolean splitClusterAs(String dockerClusterId, List<DockerMember> brain1, List<DockerMember> brain2) throws TException {
        return dockerClusterManager.splitClusterAs(dockerClusterId, brain1, brain2);
    }

    @Override
    public boolean mergeCluster(String dockerClusterId) throws TException {
        return dockerClusterManager.mergeCluster(dockerClusterId);
    }

    @Override
    public void loginToHazelcastCloudUsingEnvironment() throws TException {
        if(cloudManager == null)
            cloudManager = new HazelcastCloudManager();
        cloudManager.loginToHazelcastCloudUsingEnvironment();
    }

    @Override
    public void loginToHazelcastCloud(String uri, String apiKey, String apiSecret) throws TException {
        if(cloudManager == null)
            cloudManager = new HazelcastCloudManager();
        cloudManager.loginToHazelcastCloud(uri, apiKey, apiSecret);
    }

    @Override
    public CloudCluster createHazelcastCloudStandardCluster(String hazelcastVersion, boolean isTlsEnabled) throws TException {
        return getCloudManager().createHazelcastCloudStandardCluster(hazelcastVersion, isTlsEnabled);
    }

    @Override
    public void setHazelcastCloudClusterMemberCount(String id, int totalMemberCount) throws TException {
        getCloudManager().setHazelcastCloudClusterMemberCount(id, totalMemberCount);
    }

    @Override
    public CloudCluster getHazelcastCloudCluster(String id) throws TException {
        return getCloudManager().getHazelcastCloudCluster(id);
    }

    @Override
    public CloudCluster stopHazelcastCloudCluster(String id) throws TException {
        return getCloudManager().stopHazelcastCloudCluster(id);
    }

    @Override
    public CloudCluster resumeHazelcastCloudCluster(String id) throws TException {
        return getCloudManager().resumeHazelcastCloudCluster(id);
    }

    @Override
    public void deleteHazelcastCloudCluster(String id) throws TException {
        getCloudManager().deleteHazelcastCloudCluster(id);
    }

    private HazelcastCloudManager getCloudManager() throws CloudException {
        if(cloudManager == null)
            throw new CloudException("It seems cloud manager is null, did you login?");
        return cloudManager;
    }

    @Override
    public Response executeOnController(String clusterId, String script, Lang lang) throws TException {
        //TODO
        ScriptEngineManager scriptEngineManager = ScriptEngineManagerContext.getScriptEngineManager();
        String engineName = lang.name().toLowerCase(Locale.ENGLISH);
        ScriptEngine engine = scriptEngineManager.getEngineByName(engineName);
        if (engine == null) {
            throw new IllegalArgumentException("Could not find ScriptEngine named:" + engineName);
        }
        
        // Interpret `clusterId` as null if it is empty
        // Because thrift cpp-bindings don't support null String.
        if (clusterId != null && !clusterId.isEmpty()) {
            int i = 0;
            for (HazelcastInstance instance : clusterManager.getCluster(clusterId).getInstances()) {
                engine.put("instance_" + i++, instance);
            }
        }
        Response response = new Response();
        try {
            engine.eval(script);
            Object result = engine.get("result");
            if (result instanceof Throwable) {
                response.setMessage(((Throwable) result).getMessage());
            } else if (result instanceof byte[]) {
                response.setResult((byte[]) result);
            } else if (result instanceof String) {
                response.setResult(((String) result).getBytes("utf-8"));
            }
            response.setSuccess(true);
        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
        }
        return response;
    }

}
