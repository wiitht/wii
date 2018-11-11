# 简介:
![license](https://img.shields.io/badge/license-Apache--2.0-green.svg)  
wii为微服务之间提供了一层代理，用于解决服务之间调用管理问题(当然你也可以用来做网关)。支持HTTP1.x、HTTP2、GRPC调用；其特性如下：
* 底层采用netty作代理；
* 具备服务发现、负载均衡、超时重试限流、健康检查、容错处理、流量控制、链路追踪、统计等功能；
* 集成Springboot；

# 内容：
### wii-core：
wii的核心模块，封装底层netty proxy;

### wii-dex: 
wii的基础模块，对服务发现，做负载均衡，做限流容错控制，做链路追踪等等适配；

### wii-control: 
wii-control用于服务治理控制的组件提供动态策略更新的功能，原理是将底层的控制功能以接口的形式开放给上层调用控制内容包括；
* 配置动态刷新
* 流控策略
* 统计信息上报
### wii-access:
wii-access是一个对认证，权限，安全等等进行封装的组件；

### spring-boot-starter-wii
集成springboot，以及consul、zipkin、sentinel、ribbon等等组件； 


