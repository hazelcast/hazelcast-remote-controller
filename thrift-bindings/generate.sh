#!/bin/sh

GEN_LANG="$1"

THRIFT_FILE="remotecontroller.thrift"

PYTHON_PATH="../python-controller"
JAVA_PATH="../server-container/src/main/java"
NODEJS_PATH="../nodejs-controller/lib"
CSHARP_PATH="../csharp-controller"
GOLANG_PATH="../golang-controller"
CPP_PATH="../cpp-controller"

echo $"Generating Thrift bindings for ${GEN_LANG}"

case ${GEN_LANG} in
    java)
        thrift -r --gen java -out ${JAVA_PATH} ${THRIFT_FILE}
        ;;
    py)
        thrift -r --gen py:new_style,utf8strings -out ${PYTHON_PATH} ${THRIFT_FILE}
        ;;
    nodejs)
        thrift -r --gen js:node -out ${NODEJS_PATH} ${THRIFT_FILE}
        ;;
    csharp)
        thrift -r --gen csharp -out ${CSHARP_PATH} ${THRIFT_FILE}
        ;;
    go)
        thrift -r --gen go -out ${GOLANG_PATH} ${THRIFT_FILE}
        ;;
    cpp)
        thrift -r --gen cpp -out ${CPP_PATH} ${THRIFT_FILE}
        ;;
    *)
        echo $"$1 not supported"
esac
