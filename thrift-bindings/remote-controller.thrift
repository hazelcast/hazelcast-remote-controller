namespace java com.hazelcast.remotecontroller
namespace py hzrc
namespace csharp Hazelcast.Remote


struct Cluster{
    1:string id;
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
service RemoteController {
    bool ping();
    bool clean();
    bool exit();

    Cluster createCluster(1:string hzVersion, 2:string xmlconfig) throws (1:ServerException serverException);

    Member startMember(1:string clusterId) throws (1:ServerException serverException);
    bool shutdownMember(1:string clusterId, 2:string memberId);
    bool terminateMember(1:string clusterId, 2:string memberId);
    bool suspendMember(1:string clusterId, 2:string memberId);
    bool resumeMember(1:string clusterId, 2:string memberId);

    bool shutdownCluster(1:string clusterId);
    bool terminateCluster(1:string clusterId);

    Cluster splitMemberFromCluster(1:string memberId)
    Cluster mergeMemberToCluster(1:string clusterId, 2:string memberId)

    Response executeOnController(1:string clusterId, 2:string script, 3:Lang lang);

}

