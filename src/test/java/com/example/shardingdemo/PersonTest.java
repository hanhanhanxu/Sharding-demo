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
