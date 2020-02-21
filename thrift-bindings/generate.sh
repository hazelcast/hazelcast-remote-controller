#!/bin/sh

GEN_LANG="$1"

THRIFT_FILE="remote-controller.thrift"

JAVA_PATH="../src/main/java"
PYTHON_PATH="./python"
NODEJS_PATH="./nodejs"
CSHARP_PATH="./netstd"
GOLANG_PATH="./golang"

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
        thrift -r --gen netstd -out ${CSHARP_PATH} ${THRIFT_FILE}
        ;;
    go)
        thrift -r --gen go -out ${GOLANG_PATH} ${THRIFT_FILE}
        ;;
    *)
        echo $"$1 not supported"
esac
