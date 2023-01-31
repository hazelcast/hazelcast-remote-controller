// Code generated by Thrift Compiler (0.15.0). DO NOT EDIT.

package main

import (
	"context"
	"flag"
	"fmt"
	"math"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	thrift "github.com/apache/thrift/lib/go/thrift"
	"remote_controller"
)

var _ = remote_controller.GoUnusedProtection__

func Usage() {
  fmt.Fprintln(os.Stderr, "Usage of ", os.Args[0], " [-h host:port] [-u url] [-f[ramed]] function [arg1 [arg2...]]:")
  flag.PrintDefaults()
  fmt.Fprintln(os.Stderr, "\nFunctions:")
  fmt.Fprintln(os.Stderr, "  bool ping()")
  fmt.Fprintln(os.Stderr, "  bool clean()")
  fmt.Fprintln(os.Stderr, "  bool exit()")
  fmt.Fprintln(os.Stderr, "  Cluster createCluster(string hzVersion, string xmlconfig)")
  fmt.Fprintln(os.Stderr, "  Cluster createClusterKeepClusterName(string hzVersion, string xmlconfig)")
  fmt.Fprintln(os.Stderr, "  Member startMember(string clusterId)")
  fmt.Fprintln(os.Stderr, "  bool shutdownMember(string clusterId, string memberId)")
  fmt.Fprintln(os.Stderr, "  bool terminateMember(string clusterId, string memberId)")
  fmt.Fprintln(os.Stderr, "  bool suspendMember(string clusterId, string memberId)")
  fmt.Fprintln(os.Stderr, "  bool resumeMember(string clusterId, string memberId)")
  fmt.Fprintln(os.Stderr, "  bool shutdownCluster(string clusterId)")
  fmt.Fprintln(os.Stderr, "  bool terminateCluster(string clusterId)")
  fmt.Fprintln(os.Stderr, "  Cluster splitMemberFromCluster(string memberId)")
  fmt.Fprintln(os.Stderr, "  Cluster mergeMemberToCluster(string clusterId, string memberId)")
  fmt.Fprintln(os.Stderr, "  void loginToCloudUsingEnvironment()")
  fmt.Fprintln(os.Stderr, "  void loginToCloud(string baseUrl, string apiKey, string apiSecret)")
  fmt.Fprintln(os.Stderr, "  CloudCluster createCloudCluster(string hazelcastVersion, bool isTlsEnabled)")
  fmt.Fprintln(os.Stderr, "  CloudCluster getCloudCluster(string cloudClusterId)")
  fmt.Fprintln(os.Stderr, "  CloudCluster stopCloudCluster(string cloudClusterId)")
  fmt.Fprintln(os.Stderr, "  CloudCluster resumeCloudCluster(string cloudClusterId)")
  fmt.Fprintln(os.Stderr, "  void deleteCloudCluster(string cloudClusterId)")
  fmt.Fprintln(os.Stderr, "  Response executeOnController(string clusterId, string script, Lang lang)")
  fmt.Fprintln(os.Stderr)
  os.Exit(0)
}

type httpHeaders map[string]string

func (h httpHeaders) String() string {
  var m map[string]string = h
  return fmt.Sprintf("%s", m)
}

func (h httpHeaders) Set(value string) error {
  parts := strings.Split(value, ": ")
  if len(parts) != 2 {
    return fmt.Errorf("header should be of format 'Key: Value'")
  }
  h[parts[0]] = parts[1]
  return nil
}

func main() {
  flag.Usage = Usage
  var host string
  var port int
  var protocol string
  var urlString string
  var framed bool
  var useHttp bool
  headers := make(httpHeaders)
  var parsedUrl *url.URL
  var trans thrift.TTransport
  _ = strconv.Atoi
  _ = math.Abs
  flag.Usage = Usage
  flag.StringVar(&host, "h", "localhost", "Specify host and port")
  flag.IntVar(&port, "p", 9090, "Specify port")
  flag.StringVar(&protocol, "P", "binary", "Specify the protocol (binary, compact, simplejson, json)")
  flag.StringVar(&urlString, "u", "", "Specify the url")
  flag.BoolVar(&framed, "framed", false, "Use framed transport")
  flag.BoolVar(&useHttp, "http", false, "Use http")
  flag.Var(headers, "H", "Headers to set on the http(s) request (e.g. -H \"Key: Value\")")
  flag.Parse()
  
  if len(urlString) > 0 {
    var err error
    parsedUrl, err = url.Parse(urlString)
    if err != nil {
      fmt.Fprintln(os.Stderr, "Error parsing URL: ", err)
      flag.Usage()
    }
    host = parsedUrl.Host
    useHttp = len(parsedUrl.Scheme) <= 0 || parsedUrl.Scheme == "http" || parsedUrl.Scheme == "https"
  } else if useHttp {
    _, err := url.Parse(fmt.Sprint("http://", host, ":", port))
    if err != nil {
      fmt.Fprintln(os.Stderr, "Error parsing URL: ", err)
      flag.Usage()
    }
  }
  
  cmd := flag.Arg(0)
  var err error
  var cfg *thrift.TConfiguration = nil
  if useHttp {
    trans, err = thrift.NewTHttpClient(parsedUrl.String())
    if len(headers) > 0 {
      httptrans := trans.(*thrift.THttpClient)
      for key, value := range headers {
        httptrans.SetHeader(key, value)
      }
    }
  } else {
    portStr := fmt.Sprint(port)
    if strings.Contains(host, ":") {
           host, portStr, err = net.SplitHostPort(host)
           if err != nil {
                   fmt.Fprintln(os.Stderr, "error with host:", err)
                   os.Exit(1)
           }
    }
    trans = thrift.NewTSocketConf(net.JoinHostPort(host, portStr), cfg)
    if err != nil {
      fmt.Fprintln(os.Stderr, "error resolving address:", err)
      os.Exit(1)
    }
    if framed {
      trans = thrift.NewTFramedTransportConf(trans, cfg)
    }
  }
  if err != nil {
    fmt.Fprintln(os.Stderr, "Error creating transport", err)
    os.Exit(1)
  }
  defer trans.Close()
  var protocolFactory thrift.TProtocolFactory
  switch protocol {
  case "compact":
    protocolFactory = thrift.NewTCompactProtocolFactoryConf(cfg)
    break
  case "simplejson":
    protocolFactory = thrift.NewTSimpleJSONProtocolFactoryConf(cfg)
    break
  case "json":
    protocolFactory = thrift.NewTJSONProtocolFactory()
    break
  case "binary", "":
    protocolFactory = thrift.NewTBinaryProtocolFactoryConf(cfg)
    break
  default:
    fmt.Fprintln(os.Stderr, "Invalid protocol specified: ", protocol)
    Usage()
    os.Exit(1)
  }
  iprot := protocolFactory.GetProtocol(trans)
  oprot := protocolFactory.GetProtocol(trans)
  client := remote_controller.NewRemoteControllerClient(thrift.NewTStandardClient(iprot, oprot))
  if err := trans.Open(); err != nil {
    fmt.Fprintln(os.Stderr, "Error opening socket to ", host, ":", port, " ", err)
    os.Exit(1)
  }
  
  switch cmd {
  case "ping":
    if flag.NArg() - 1 != 0 {
      fmt.Fprintln(os.Stderr, "Ping requires 0 args")
      flag.Usage()
    }
    fmt.Print(client.Ping(context.Background()))
    fmt.Print("\n")
    break
  case "clean":
    if flag.NArg() - 1 != 0 {
      fmt.Fprintln(os.Stderr, "Clean requires 0 args")
      flag.Usage()
    }
    fmt.Print(client.Clean(context.Background()))
    fmt.Print("\n")
    break
  case "exit":
    if flag.NArg() - 1 != 0 {
      fmt.Fprintln(os.Stderr, "Exit requires 0 args")
      flag.Usage()
    }
    fmt.Print(client.Exit(context.Background()))
    fmt.Print("\n")
    break
  case "createCluster":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "CreateCluster requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.CreateCluster(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "createClusterKeepClusterName":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "CreateClusterKeepClusterName requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.CreateClusterKeepClusterName(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "startMember":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "StartMember requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.StartMember(context.Background(), value0))
    fmt.Print("\n")
    break
  case "shutdownMember":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "ShutdownMember requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.ShutdownMember(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "terminateMember":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "TerminateMember requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.TerminateMember(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "suspendMember":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "SuspendMember requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.SuspendMember(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "resumeMember":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "ResumeMember requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.ResumeMember(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "shutdownCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "ShutdownCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.ShutdownCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "terminateCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "TerminateCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.TerminateCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "splitMemberFromCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "SplitMemberFromCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.SplitMemberFromCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "mergeMemberToCluster":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "MergeMemberToCluster requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    fmt.Print(client.MergeMemberToCluster(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "loginToCloudUsingEnvironment":
    if flag.NArg() - 1 != 0 {
      fmt.Fprintln(os.Stderr, "LoginToCloudUsingEnvironment requires 0 args")
      flag.Usage()
    }
    fmt.Print(client.LoginToCloudUsingEnvironment(context.Background()))
    fmt.Print("\n")
    break
  case "loginToCloud":
    if flag.NArg() - 1 != 3 {
      fmt.Fprintln(os.Stderr, "LoginToCloud requires 3 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    argvalue2 := flag.Arg(3)
    value2 := argvalue2
    fmt.Print(client.LoginToCloud(context.Background(), value0, value1, value2))
    fmt.Print("\n")
    break
  case "createCloudCluster":
    if flag.NArg() - 1 != 2 {
      fmt.Fprintln(os.Stderr, "CreateCloudCluster requires 2 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2) == "true"
    value1 := argvalue1
    fmt.Print(client.CreateCloudCluster(context.Background(), value0, value1))
    fmt.Print("\n")
    break
  case "getCloudCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "GetCloudCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.GetCloudCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "stopCloudCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "StopCloudCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.StopCloudCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "resumeCloudCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "ResumeCloudCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.ResumeCloudCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "deleteCloudCluster":
    if flag.NArg() - 1 != 1 {
      fmt.Fprintln(os.Stderr, "DeleteCloudCluster requires 1 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    fmt.Print(client.DeleteCloudCluster(context.Background(), value0))
    fmt.Print("\n")
    break
  case "executeOnController":
    if flag.NArg() - 1 != 3 {
      fmt.Fprintln(os.Stderr, "ExecuteOnController requires 3 args")
      flag.Usage()
    }
    argvalue0 := flag.Arg(1)
    value0 := argvalue0
    argvalue1 := flag.Arg(2)
    value1 := argvalue1
    tmp2, err := (strconv.Atoi(flag.Arg(3)))
    if err != nil {
      Usage()
     return
    }
    argvalue2 := remote_controller.Lang(tmp2)
    value2 := argvalue2
    fmt.Print(client.ExecuteOnController(context.Background(), value0, value1, value2))
    fmt.Print("\n")
    break
  case "":
    Usage()
    break
  default:
    fmt.Fprintln(os.Stderr, "Invalid function ", cmd)
  }
}
