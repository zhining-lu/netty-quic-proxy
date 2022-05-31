# netty-websocket-proxy
A implementation of Forward-proxy in Java base on netty4 framework uses quic protocol.

# Features
- [x] QUIC support
- [x] UDP support
- [x] SOCKS5 support

# Environment
* JRE8

# Install
1. download netty-quic-proxy-x.x.x-bin.tar.gz
2. tar -xzvf netty-quic-proxy-x.x.x-bin.tar.gz
3. run
#### as swserver
```
java -jar ./bin/netty-quic-proxy-x.x.x.jar -s -conf="./conf/config-example-server.json"
```
#### as swclient
```
java -jar ./bin/netty-quic-proxy-x.x.x.jar -c -conf="./conf/config-example-client.json"
```
  Note: You can also use the command under bin to start the service. After the service starts, you can use Google Chrome and install the SwitchyOmega plug-in to surf the Internet

# Build
1. import as maven project
2. maven package

## TODO
* [ ] performance optimization
* [ ] android client
