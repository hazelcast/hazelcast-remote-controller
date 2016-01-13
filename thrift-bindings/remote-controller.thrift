namespace java com.hazelcast.remotecontroller
namespace py hzrc
namespace csharp Hazelcast.RemoteController


struct Cluster{
    1:string id;
}

struct Member{
    1:string uuid;
    2:string host;
    3:i32 port;
}

enum Lang{
    JS = 1,
    GROOVY = 2,
    JYTHON = 3,
    JRUBY = 4
}

exception ServerException {
    1:string message;
}
service RemoteController {
    bool ping();
    bool clean();
    bool exit();

    Cluster createCluster(1:string hzVersion, 2:string xmlconfig) throws (1:ServerException serverException);

    Member startMember(1:string clusterId) throws (1:ServerException serverException);
    bool shutdownMember(1:string clusterId, 2:string memberId);
    bool terminateMember(1:string clusterId, 2:string memberId);

    bool shutdownCluster(1:string clusterId);
    bool terminateCluster(1:string clusterId);

    Cluster splitMemberFromCluster(1:string memberId)
    Cluster mergeMemberToCluster(1:string clusterId, 2:string memberId)

    bool executeOnController(1:string clusterId, 2:string script, 3:Lang lang);

}

