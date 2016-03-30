var thrift = require('thrift');
var RemoteController = require('./RemoteController');

function HzRemoteController(host, port) {

    var transport = thrift.TBufferedTransport();
    var protocol =  thrift.TBinaryProtocol();

    var connection = thrift.createConnection('localhost', 9701, {
        transport: transport,
        protocol: protocol
    });

    connection.on('error', function(err) {
        console.log(err);
    });

    this.client = thrift.createClient(RemoteController, connection);
}

HzRemoteController.prototype.ping = function(callback) {
    return this.client.ping(callback);
};

HzRemoteController.prototype.clean = function(callback) {
    return this.client.clean(callback);
};

HzRemoteController.prototype.exit = function(callback) {
    return this.client.exit(callback);
};

HzRemoteController.prototype.createCluster = function(hzVersion, xmlConfig, callback) {
    return this.client.createCluster(hzVersion, xmlConfig, callback);
};

HzRemoteController.prototype.startMember = function(clusterId, callback) {
    return this.client.startMember(clusterId, callback);
};

HzRemoteController.prototype.shutdownMember = function(clusterId, memberId, callback) {
    return this.client.shutdownMember(clusterId, memberId, callback);
};

HzRemoteController.prototype.terminateMember = function(clusterId, memberId, callback) {
    return this.client.terminateMember(clusterId, memberId, callback);
};

HzRemoteController.prototype.shutdownCluster = function(clusterId, callback) {
    return this.client.shutdownCluster(clusterId, callback);
};

HzRemoteController.prototype.terminateCluster = function(clusterId, callback) {
    return this.client.terminateCluster(clusterId, callback);
};

HzRemoteController.prototype.splitMemberFromCluster = function(memberId, callback) {
    return this.client.splitMemberFromCluster(memberId, callback);
};

HzRemoteController.prototype.mergeMemberToCluster = function(clusterId, memberId, callback) {
    return this.client.mergeMemberToCluster(clusterId, memberId, callback);
};

HzRemoteController.prototype.executeOnController = function(clusterId, script, lang, callback) {
    return this.client.executeOnController(clusterId, script, lang, callback);
};

module.exports = HzRemoteController;