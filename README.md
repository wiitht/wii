# wii:
Wii是为微服务提供基础设施组件的一套体系；目的是希望能够为微服务治理带来一些更加适合我们使用的方案。
## 基础组件：
### wii-dex: 
wii-dex组件是一个类似sidecar的中间件，支持GRPC，Hessian，http并提供客户端和服务端调用模块；代理服务上支持基于不同协议，服务注册发现的负载均衡实现(底层基于Ribbon实现)，并且提供代理配置和服务注册(目前只考虑consul)的配置；同时希望提供容错(hystrix)处理，链路追踪(zipkin)，流量监控，健康检查，超时重试等等机制；

### wii-gateway：
Kong在服务前端做一级网关是比较理想的选择，其内部也是基于openrestry实现并且提供了非常丰富的治理功能；wii-gateway主要是基于Spring cloud Gateway实现作为应用的二级网关使用；

### wii-control: 
wii-control用于服务治理控制的组件可以整合到应用服务，gateway，sidecar之中，后续希望提供一个统一的平台集中的对应用服务，gateway, sidecar下发治理命令；达到按照定制化的需求对服务进行降级，限流，扩容等等；

### wii-access:
wii-access是一个对认证，权限，安全等等进行封装的组件；

### wii-storage:
wii-storage是一个基于sharding-jdbc的存储组件，可以支持多租户方式(一个用户对应一个数据库，一个应用访问多个数据库)只需要简单配置即可；也可以使用其他的分片，分库，分表功能；

## DevOps管理后台：
考虑提供一个管理后台，内容包括持续交付，自动化测试，服务治理中心，监控平台，运营分析等等功能模块；

## 与istio组合使用：
一级网关采用istio gateway，二级网关应用wii-gateway；应用服务上可以沿用wii-control，wii-access；其他全部采用istio的解决方案；既满足自己定制化的策略控制，同时也能充分利用istio带来的规模部署能力和强大性能保障；
