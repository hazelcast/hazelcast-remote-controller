#!/bin/sh

GEN_LANG="$1"

THRIFT_FILE="remote_controller.thrift"
THRIFT=${THRIFT:-thrift}

JAVA_PATH="../src/main/java"
PYTHON_PATH="./python"
NODEJS_PATH="./nodejs"
CSHARP_PATH="./netstd"
GOLANG_PATH="./golang"
CPP_PATH="../cpp-controller"

echo $"Generating Thrift bindings for ${GEN_LANG}"

case ${GEN_LANG} in
    java)
        $THRIFT -r --gen java -out ${JAVA_PATH} ${THRIFT_FILE}
        ;;
    py)
        $THRIFT -r --gen py:new_style,utf8strings -out ${PYTHON_PATH} ${THRIFT_FILE}
        ;;
    nodejs)
        $THRIFT -r --gen js:node -out ${NODEJS_PATH} ${THRIFT_FILE}
        ;;
    csharp)
        $THRIFT -r --gen netstd -out ${CSHARP_PATH} ${THRIFT_FILE}
        ;;
    go)
        $THRIFT -r --gen go -out ${GOLANG_PATH} ${THRIFT_FILE}
        ;;
    cpp)
        $THRIFT -r --gen cpp -out ${CPP_PATH}  ${THRIFT_FILE}
        ;;
    *)
        echo $"$1 not supported"
esac
