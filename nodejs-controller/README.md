# Hazelcast Remote Controller for Node.js
## Build
```shell
git clone git@github.com:hazelcast/hazelcast-remote-controller.git
cd hazelcast-remote-controller/nodejs-controller
npm install
```
## Use From Other Projects
Because Hazelcast Remote Controller for Node.js is not published to NPM, you need to locally link this project from
other projects you would like to use it with.
After building the project, run.
```shell
npm link
```
Change working directory to root of the dependant project.

Run
```shell
npm link hazelcast-remote-controller
```
Import it like any other Javascript module.
```javascript
var RemoteContoller = require('hazelcast-remote-controller')
```
### Note:
In order to use remote controller client, remote controller server should be up and running. To do that, go to root
of `hazelcast-remote-controller` project and run:
```shell
mvn -f server-container/ exec:java -Dexec.mainClass="com.hazelcast.remotecontroller.Main"
```