# Hazelcast Remote Controller

Hazelcast Cluster lifecycle manager for native client tests. Server container and language clients provide easy management of server side from clients

### Cluster methods running with server jars

* Create a cluster with a provided xml config
* Start a Member in the configured cluster
* Shutdown a member
* Terminate a member
* Remote script execution on the container

### Cloud API usage
#### Pre-request for usage of Cloud API
Cloud credentials should be added as an env variable; baseUrl, apiKey, apiSecret; i.e. baseUrl=https://uat.hazelcast.cloud
#### API for native clients
* Create Standard/Enterprise and SSL enabled/disabled hazelcast cloud cluster
* Stop/Resume/Delete cloud cluster
* Scale up/down cloud cluster

Project uses Apache Thrift to provide multi language support.


### Mail Group

Please join the mail group if you are interested in using or developing Hazelcast.

[http://groups.google.com/group/hazelcast](http://groups.google.com/group/hazelcast)

#### License

Hazelcast Remote Controller is available under the Apache 2 License. 

#### Copyright

Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.

Visit [www.hazelcast.com](http://www.hazelcast.com/) for more info.