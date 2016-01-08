#!/bin/sh

GEN_LANG="$1"

THRIFT_FILE="../thrift/remote-controller.thrift"

PYTHON_PATH="../../../../hazelcast-remotecontroller-python"

ECHO $"Generating Thrift bindings for ${GEN_LANG}"

case ${GEN_LANG} in
    java)
        thrift -r --gen java -out ../java ${THRIFT_FILE}
        ;;
    py)
        thrift -r --gen py:new_style,utf8strings -out ${PYTHON_PATH} ${THRIFT_FILE}
        ;;
    js)
        thrift -r --gen js:node -out ../nodejs ${THRIFT_FILE}
        ;;
    csharp)
        thrift -r --gen csharp -out ../csharp ${THRIFT_FILE}
        ;;
    *)
        echo $"$1 not supported"
esac