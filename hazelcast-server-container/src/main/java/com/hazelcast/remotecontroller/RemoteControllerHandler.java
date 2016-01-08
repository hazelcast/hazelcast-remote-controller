package com.hazelcast.remotecontroller;

import org.apache.thrift.TException;

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
        return false;
    }

    @Override
    public Cluster createCluster(String hzVersion, String xmlconfig) throws TException {
        return clusterManager.createCluster(hzVersion, xmlconfig);
    }

    @Override
    public Member startMember(String clusterId, int delay) throws TException {
        return clusterManager.startMember(clusterId, delay);
    }

    @Override
    public boolean shutdownMember(String clusterId, String memberId, int delay) throws TException {
        return clusterManager.shutdownMember(clusterId, memberId, delay);
    }

    @Override
    public boolean terminateMember(String clusterId, String memberId, int delay) throws TException {
        return false;
    }

    @Override
    public boolean shutdownCluster(String clusterId, int delay) throws TException {
        return false;
    }

    @Override
    public boolean terminateCluster(String clusterId, int delay) throws TException {
        return false;
    }

    @Override
    public Cluster splitMemberFromCluster(String memberId, int delay) throws TException {
        return null;
    }

    @Override
    public Cluster mergeMemberToCluster(String clusterId, String memberId, int delay) throws TException {
        return null;
    }

    @Override
    public boolean executeOnController(String clusterId, String script, Lang lang) throws TException {
        return false;
    }

}
