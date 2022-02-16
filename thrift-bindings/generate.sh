#!/bin/sh

GEN_LANG="$1"

if [ -z "$GEN_LANG" ]; then
    echo "Usage: generate.sh <lang>"
    exit 1
fi

THRIFT_FILE="remote_controller.thrift"

if [ -z "$THRIFT" ]; then
    THRIFT=thrift
fi

JAVA_PATH="../src/main/java"
PYTHON_PATH="./python"
NODEJS_PATH="./nodejs"
CSHARP_PATH="./netstd"
GOLANG_PATH="./golang"
CPP_PATH="../cpp-controller"

THRIFT_VERSION=$($THRIFT -version)
if [ $? -ne 0 ]; then
    echo "Failed to run the thrift compiler ($THRIFT)"
    exit 1
fi

THRIFT_VERSION=$(echo $THRIFT_VERSION | grep -Eo '[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]')
if [ -z "$THRIFT_VERSION" ]; then
    echo "Failed to detect the thrift compiler version"
    exit 1
fi

echo $"Generating Thrift bindings for lang '${GEN_LANG}' with Thrift v${THRIFT_VERSION}"

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
        if [ "$THRIFT_VERSION" != "0.15.0" ]; then
            echo "Lang 'csharp' requires Thrift 0.15.0"
            exit 1
        fi
        $THRIFT -r --gen netstd -out ${CSHARP_PATH} ${THRIFT_FILE}
        ;;
    go)
        $THRIFT -r --gen go -out ${GOLANG_PATH} ${THRIFT_FILE}
        ;;
    cpp)
        $THRIFT -r --gen cpp -out ${CPP_PATH}  ${THRIFT_FILE}
        ;;
    *)
        echo $"Lang '$1' not supported"
esac
