package com.hazelcast.remotecontroller;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.management.ScriptEngineManagerContext;
import org.apache.thrift.TException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class RemoteControllerHandler implements RemoteController.Iface {

    private ClusterManager clusterManager;

    public RemoteControllerHandler() {
        this.clusterManager = new ClusterManager();
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
        return clusterManager.createCluster(hzVersion, xmlconfig);
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
    public boolean shutdownCluster(String clusterId) throws TException {
        return clusterManager.shutdownCluster(clusterId);
    }

    @Override
    public boolean terminateCluster(String clusterId) throws TException {
        return clusterManager.terminateCluster(clusterId);
    }

    @Override
    public Cluster splitMemberFromCluster(String memberId) throws TException {
        //TODO
        return null;
    }

    @Override
    public Cluster mergeMemberToCluster(String clusterId, String memberId) throws TException {
        //TODO
        return null;
    }

    @Override
    public Response executeOnController(String clusterId, String script, Lang lang) throws TException {
        //TODO
        ScriptEngineManager scriptEngineManager = ScriptEngineManagerContext.getScriptEngineManager();
        String engineName = lang.name().toLowerCase();
        ScriptEngine engine = scriptEngineManager.getEngineByName(engineName);
        if (engine == null) {
            throw new IllegalArgumentException("Could not find ScriptEngine named:" + engineName);
        }
        if (clusterId != null) {
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

    @Override
    public boolean setAttributes(Cluster cluster, Member member) throws TException {
        return clusterManager.setAttributes(cluster.getId(), member.uuid);
    }

}
