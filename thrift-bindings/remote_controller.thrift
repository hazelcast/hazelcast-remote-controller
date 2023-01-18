namespace java com.hazelcast.remotecontroller
namespace py hzrc
namespace netstd Hazelcast.Remote
namespace cpp hazelcast.client.test.remote


struct Cluster{
    1:string id;
}

struct DockerCluster{
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

struct DockerMember{
    1:string containerId;
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
    bool shutdownCluster(1:string clusterId);
    bool terminateCluster(1:string clusterId);

    Member startMember(1:string clusterId) throws (1:ServerException serverException);
    bool shutdownMember(1:string clusterId, 2:string memberId);
    bool terminateMember(1:string clusterId, 2:string memberId);
    bool suspendMember(1:string clusterId, 2:string memberId);
    bool resumeMember(1:string clusterId, 2:string memberId);

    // Docker related methods follow
    DockerCluster createDockerCluster(1:string dockerImageString, 2:string xmlconfigPath) throws (1:ServerException serverException);
    bool shutdownDockerCluster(1:string dockerClusterId);

    DockerMember startDockerMember(1:string dockerClusterId) throws (1:ServerException serverException);
    bool shutdownDockerMember(1:string dockerClusterId, 2:string containerId);

    bool splitClusterAs(1:string dockerClusterId, 2:list<DockerMember> brain1, 3:list<DockerMember> brain2);
    bool mergeCluster(1:string dockerClusterId);

    /**
     * Reads the environment variables and calls loginToHazelcastCloud() method with these variables.
     * @throws CloudException
     */
    void loginToHazelcastCloudUsingEnvironment() throws (1:CloudException cloudException)

    /**
     * Logins the hazelcast cloud, sets the bearer token, uri and baseUrl to HazelcastCloudManager then it will be ready to use cloud API
     * @throws CloudException
     *
     * @param baseUrl -> Base url of the cloud environment. i.e. https://uat.hazelcast.cloud
     * @param apiKey -> Api key of the hazelcast cloud
     * @param apiSecret -> Api secret of the hazelcast cloud
     */
    void loginToHazelcastCloud(1:string baseUrl, 2:string apiKey, 3:string apiSecret) throws (1:CloudException cloudException)

    /**
     * Creates a standard cluster
     * @return CloudCluster
     * @throws CloudException
     *
     * @param hazelcastVersion
     * @param isTlsEnabled -> True if ssl enabled cluster is requested, otherwise false.
     */
    CloudCluster createHazelcastCloudStandardCluster(1:string hazelcastVersion, 2:bool isTlsEnabled) throws (1:CloudException cloudException)

    /**
     * Setting member count of the cluster
     * @throws CloudException
     *
     * @param cloudClusterId
     * @param totalMemberCount -> Total member count of the cluster
     */
    void setHazelcastCloudClusterMemberCount(1:string cloudClusterId, 2:i32 totalMemberCount) throws (1:CloudException cloudException)

    /**
     * Get information of the given cluster
     * @return CloudCluster
     * @throws CloudException
     *
     * @param cloudClusterId
     */
    CloudCluster getHazelcastCloudCluster(1:string cloudClusterId) throws (1:CloudException cloudException)

    /**
     * Stops the given cluster
     * @return CloudCluster
     * @throws CloudException
     *
     * @param cloudClusterId
     */
    CloudCluster stopHazelcastCloudCluster(1:string cloudClusterId) throws (1:CloudException cloudException)

    /**
     * Resumes the given cluster
     * @return CloudCluster
     * @throws CloudException
     *
     * @param cloudClusterId
     */
    CloudCluster resumeHazelcastCloudCluster(1:string cloudClusterId) throws (1:CloudException cloudException)

    /**
     * Deletes the given cluster
     * @return boolean
     * @throws CloudException
     *
     * @param cloudClusterId
     */
    void deleteHazelcastCloudCluster(1:string cloudClusterId) throws (1:CloudException cloudException)

    Response executeOnController(1:string clusterId, 2:string script, 3:Lang lang);

}

