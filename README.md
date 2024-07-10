## 一、主从复制

Mysql的主从复制是一个异步的复制过程：一台或多台Mysql数据库（slave，即从库）从另一台Mysql数据库（master，即主库）进行日志的复制然后再解析日志应用到自身。最终实现只对主库进行写操作，但是从库却和主库数据保持一致的效果。这是基于Mysql自带的二进制日志实现的，无需第三方工具。

主从复制一般配合读写分离使用。写主库，读从库。



具体来说Mysql主从复制分三步：

1、master将自身改变记录到二进制日志（binary log）。

2、slave将master的binary log拷贝到它的中继日志（relay log）中。

3、slave执行中继日志中的事件，将改变应用到自己的数据库中。

![](https://minio.riun.xyz/riun1/2024-07-09_69Ot3z4URrsjg6Hvyi.jpg)

注意：

- 在Mysql的主从复制中，主库一般只有一个，从库可以多个。
- 配置主从复制时，主库不能进行任何写操作。

- 先安装好两台Mysql服务，再做以下配置。



### 主库配置

1、修改Mysql数据库配置文件my.cnf

```sh
[mysqld]
# 启动二进制日志功能
log-bin=mysql-bin
#服务器唯一id，需要在这几个主从复制的机器当中是唯一的
server-id=100
```



2、重启Mysql服务：systemctl restart mysqld



3、登录Mysql执行：GRANT REPLICATION SLAVE ON *.* to 'xiaoming'@'%' identified by 'Root@123456';

如果你使用的是Mysql8，则会报错。因为Mysql8不允许GRANT 和identified一起执行，创建用户和分配权限必须分开执行：

```sh
# 创建用户xiaoming,并设置密码，运行任何主机连接
CREATE USER 'xiaoming'@'%' IDENTIFIED BY 'Root@123456';
# 授予xiaoming权限REPLICATION SLAVE，作用在任何库表上
GRANT REPLICATION SLAVE ON *.* TO 'xiaoming'@'%';
# 刷新权限
FLUSH PRIVILEGES;
```

上述sql创建了一个用户xiaoming，密码是Root@123456，并给这个用户赋予REPLICATION SLAVE权限。这是为了让从库可以从主库复制binary log。



创建完成之后，建议在navicat之类的工具中使用这个新创建的用户xiaoming尝试连接， 如果连接失败且报错密码cachexxx错误，则可以用以下方式解决：

```sh
-- 设置密码并同时修改身份验证插件为 caching_sha2_password
ALTER USER 'xiaoming'@'%' IDENTIFIED WITH caching_sha2_password BY 'Root@123456';

-- 再次修改身份验证插件为 mysql_native_password
ALTER USER 'xiaoming'@'%' IDENTIFIED WITH mysql_native_password BY 'Root@123456';

# 刷新权限
FLUSH PRIVILEGES;
```

这是因为默认创建出来的密码是caching_sha2_password身份验证的，我们需要将验证方式改为mysql_native_password。如果使用caching_sha2_password则在你的主库上需要做额外的配置否则从库和客户端都无法连接。



4、在Mysql中执行：show master status

> 执行完这个操作后主库不要再执行任何命令，停止读写。因为一旦执行其他操作，File和Position都会变化。

![](https://minio.riun.xyz/riun1/2024-07-09_69OJkeciRLhfM8A1XM.jpg)



### 从库配置

> 有几个从库，这几个从库就都要配置

1、修改Mysql数据库配置文件my.cnf

```sh
[mysqld]
#服务器唯一id
server-id=101
```



2、重启Mysql服务：systemctl restart mysqld



3、登录Mysql执行sql：

```sql
# 这里需要修改为你的主库ip、用户名、密码、File、Position
change master to master_host='192.168.188.100',master_user='xiaoming',master_password='Root@123456',master_log_file='mysql-bin.000005',master_log_pos=441;

# 启动slave，开启io_thread线程
start slave;
```



4、执行：show slave status;

把输出内容粘贴到记事本看起来方便，查看到两个Yes，就是正常的。（前者代表io-thread，后者代表sql-thread。都是yes代表都在运行）

![](https://minio.riun.xyz/riun1/2024-07-09_69OSJaSfky6qprUCLJ.jpg)



### 问题排查

如果你不慎在主库上执行了change master to xxx的命令，则可能需要做以下操作

```sh
# 停止复制进程
stop slave;

# 清除服务器上的复制信息和中继日志文件：
RESET SLAVE;

# 如果存在中继日志文件，删除它们：
# 在 MySQL 数据目录下找到并删除中继日志文件
rm -f relay-log.info
rm -f relay-log.[index_number]
```





### 测试效果与注意事项

接着就能测试了：在主库创建数据库、表、插入数据，都会自动同步到从库中去。

注意，主从复制配置完成之前，主库的所有库表都不会自动同步到从库中去。因此需要在change master to之前，把主库已存在的库表都手动同步到从库上。





##  二、读写分离

为什么要读写分离？对于大量系统而言都是读多写少的，面对日益增加的系统访问量，数据库的吞吐量面临巨大瓶颈。将数据库拆分为主库和从库，能够有效的避免由数据更新导致的行所，使得整个系统的查询性能得到极大改善。

分离前：

![](https://minio.riun.xyz/riun1/2024-07-09_69OZ2MHLtBIRymz5Ff.jpg)

分离后：

![](https://minio.riun.xyz/riun1/2024-07-09_69OZFjzCMU1dUVMGrf.jpg)

### 0、sharding-jdbc

Sharding-jdbc是轻量级Java框架，在jdbc层提供额外服务。可以理解为增强版的JDBC驱动，完全兼容jdbc和各种orm框架。因此使用起来非常方便：

- 适用于任何orm框架，如jpa、Hibernate、Mybatis、Spring JDBC Template或直连jdbc。
- 支持任何第三方数据库连接池，如DBCP、C3PO、BoneCP、Druid、HikariCP等。
- 支持任意实现jdbc规范的数据库，如MySQL、Oracle、SQLServer、PostgreSQL等。



### 1、导入依赖

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>sharding-jdbc-spring-boot-starter</artifactId>
    <version>4.0.0-RC1</version>
</dependency>
```

### 2、配置读写分离规则

```yml
spring:
  main:
    # 允许bean定义覆盖配置项 否则druid的dataSource会和Sharding-jdbc的dataSource互相覆盖
    allow-bean-definition-overriding: true
  shardingsphere:
    datasource:
      names:
        master,slave
      # 主数据源
      master:
        type: com.alibaba.druid.pool.DruidDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://47.116.177.56:3306/sharding-test?characterEncoding=utf-8
        username: root
        password: hanxuxu
      # 从数据源
      slave:
        type: com.alibaba.druid.pool.DruidDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://47.116.177.56:3307/sharding-test?characterEncoding=utf-8
        username: root
        password: hanxuxusalve
    masterslave:
      # 从数据源
      load-balance-algorithm-type: round_robin #轮询
      # 最终的数据源名称
      name: dataSource
      # 主库数据源名称
      master-data-source-name: master
      # 从库数据源名称列表，多个从库名称之间用逗号分隔
      slave-data-source-names: slave
    props:
      sql:
        show: true #开启SQL显示，默认false  方便我们观察读写是哪个库（此配置非必须）
```



### 读写分离效果和代码

```java
package com.example.shardingdemo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.shardingdemo.entity.Person;
import com.example.shardingdemo.mapper.PersonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author: HanXu
 * on 2024/7/10
 * Class description:
 */
@SpringBootTest
public class PersonTest {

    @Resource
    private PersonMapper personMapper;


    @Test
    void test() {
        Person person = new Person();
        person.setAge(18);
        person.setName("riun");
        person.setDepartment("研发部");
        personMapper.insert(person);


        Integer integer = personMapper.selectCount(new QueryWrapper<>());
        System.out.println("integer = " + integer);
    }

    /* 可以看到，插入是操作的master数据库；查询是操作的slave数据库

    2024-07-10 14:27:59.346  INFO 21620 --- [           main] ShardingSphere-SQL                       : Rule Type: master-slave
    2024-07-10 14:27:59.347  INFO 21620 --- [           main] ShardingSphere-SQL                       : SQL: INSERT INTO person  ( id,
    name,
    age,
    department )  VALUES  ( ?,
    ?,
    ?,
    ? ) ::: DataSources: master
    2024-07-10 14:27:59.392  INFO 21620 --- [           main] ShardingSphere-SQL                       : Rule Type: master-slave
    2024-07-10 14:27:59.393  INFO 21620 --- [           main] ShardingSphere-SQL                       : SQL: SELECT COUNT( 1 ) FROM person ::: DataSources: slave
    integer = 1
     */
}
```

使用sharding-jdbc我们可以非常简单的实现读写分离。

完整版代码：https://github.com/hanhanhanxu/Sharding-demo



## 结语

其实，阿里云有现成的主从复制读写分离服务可以买来用。是一个代理服务器，后面连一个主库，连两个从库（可能更多）。不用我们配置任何东西，只需要将项目中数据库的url配置为代理服务器即可。其他都不用改，就自动实现主从复制读写分离了。

![](https://minio.riun.xyz/riun1/2024-07-09_69RTgvElisCVgC763C.jpg)



而在企业级开发中，如果我们的数据、流量都很小。那完全用不到主从复制读写分离。也不建议使用。用了有时候反而会有奇怪的问题。如果我们的数据、流量都很大，在增加了缓存、MQ、异步化、数据库表拆分之后还是存在数据库读写瓶颈，那就需要考虑使用主从复制读写分离了。

在使用时，一般不会选择上述方式，因为对于大数据、大流量下，上述方式可能会对已有服务有侵入，对运维和开发人员也不友好。

对于中小型企业，使用时建议直接购买阿里云已有的主从复制读写分离rds服务就可以。

对于大型企业，则需要基础设施团队自研内部的MySQL服务，包装为上述阿里云的方式，对企业内部使用时也是只需要修改数据库url地址即可。



> 部分内容参考：https://www.bilibili.com/video/BV13a411q753?p=172
