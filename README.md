# netty-quic-proxy
A implementation of Forward-proxy in Java base on netty4 framework uses quic protocol.

# Features
- [x] QUIC support
- [x] 0-RTT support
- [x] SOCKS5 support

# Environment
* JDK11

# Install
1. [download netty-quic-proxy-x.x.x.tar.gz ](https://github.com/zhining-lu/netty-quic-proxy/releases) 
2. tar -xzvf netty-quic-proxy-x.x.x.tar.gz
3. run
#### as server
```
cd netty-quic-proxy-x.x.x/bin
./start-server-quic-proxy.sh(Linux)
```
#### as client
```
cd netty-quic-proxy-x.x.x/bin
./start-local-quic-proxy.sh(Linux) or double-click start-local-quic-proxy.bat(Win)
```
Note: On the window client, you can also use v2ray-Win-Client to surf the Internet
# Build
1. import as maven project
2. maven package

## TODO
* [ ] performance optimization
* [ ] android client
