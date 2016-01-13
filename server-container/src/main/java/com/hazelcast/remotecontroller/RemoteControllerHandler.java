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
    public boolean executeOnController(String clusterId, String script, Lang lang) throws TException {
        //TODO
        return false;
    }

}
