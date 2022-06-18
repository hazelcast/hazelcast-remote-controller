namespace java com.hazelcast.remotecontroller
namespace py hzrc
namespace netstd Hazelcast.Remote
namespace cpp hazelcast.client.test.remote


struct Cluster{
    1:string id;
}

struct CloudCluster{
    1:string id;
    2:string name;
    3:string nameForConnect
    4:string hazelcastVersion;
    5:bool isTlsEnabled;
    6:string state;
    7:string token;
    8:string certificatePath
    9:string tlsPassword
}

struct Member{
    1:string uuid;
    2:string host;
    3:i32 port;
}

struct Response{
    1:bool success;
    2:string message;
    3:binary result;
}
enum Lang{
    JAVASCRIPT = 1,
    GROOVY = 2,
    PYTHON = 3,
    RUBY = 4
}

exception ServerException {
    1:string message;
}

exception CloudException {
    1:string message;
}

service RemoteController {
    bool ping();
    bool clean();
    bool exit();

    Cluster createCluster(1:string hzVersion, 2:string xmlconfig) throws (1:ServerException serverException);
    Cluster createClusterKeepClusterName(1:string hzVersion, 2:string xmlconfig) throws (1:ServerException serverException);

    Member startMember(1:string clusterId) throws (1:ServerException serverException);
    bool shutdownMember(1:string clusterId, 2:string memberId);
    bool terminateMember(1:string clusterId, 2:string memberId);
    bool suspendMember(1:string clusterId, 2:string memberId);
    bool resumeMember(1:string clusterId, 2:string memberId);

    bool shutdownCluster(1:string clusterId);
    bool terminateCluster(1:string clusterId);

    Cluster splitMemberFromCluster(1:string memberId)
    Cluster mergeMemberToCluster(1:string clusterId, 2:string memberId)

    void loginToHazelcastCloudUsingEnvironment() throws (1:CloudException cloudException)
    void loginToHazelcastCloud(1:string uri, 2:string apiKey, 3:string apiSecret) throws (1:CloudException cloudException)
    CloudCluster createHazelcastCloudStandardCluster(1:string hazelcastVersion, 2:bool isTlsEnabled) throws (1:CloudException cloudException)
    bool scaleUpDownHazelcastCloudStandardCluster(1:string id, 2:i32 scaleNumber) throws (1:CloudException cloudException)
    CloudCluster getHazelcastCloudCluster(1:string id) throws (1:CloudException cloudException)
    CloudCluster stopHazelcastCloudCluster(1:string id) throws (1:CloudException cloudException)
    CloudCluster resumeHazelcastCloudCluster(1:string id) throws (1:CloudException cloudException)
    bool deleteHazelcastCloudCluster(1:string id) throws (1:CloudException cloudException)

    Response executeOnController(1:string clusterId, 2:string script, 3:Lang lang);

}

