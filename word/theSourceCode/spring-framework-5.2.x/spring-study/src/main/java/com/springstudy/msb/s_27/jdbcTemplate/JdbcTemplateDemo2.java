package com.springstudy.msb.s_27.jdbcTemplate;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description JdbcTemplate最基本的使用
 * @date 2022/6/14 4:53 下午
 */
public class JdbcTemplateDemo2 {

	public static void main(String[] args) {
		//1.获取 Spring 容器
		ApplicationContext ac = new ClassPathXmlApplicationContext("bean.xml");
		//2.根据 id 获取 bean 对象
		JdbcTemplate jt = (JdbcTemplate) ac.getBean("jdbcTemplate");
		//3.执行操作
		jt.execute("insert into account(name,money)values('eee',500)");
		//保存
		jt.update("insert into account(name,money)values(?,?)","fff",5000);
		//修改
		jt.update("update account set money = money-? where id = ?",300,6);
		//删除
		jt.update("delete from account where id = ?",6);
		//查询所有
		// List<Account> accounts = jt.query("select * from account where money > ? ", new AccountRowMapper(), 500);
		//查询一个
		//List<Account> as = jt.query("select * from account where id = ? ", new AccountRowMapper(), 55);
		//查询返回一行一列:使用聚合函数，在不使用 group by 字句时，都是返回一行一列。最长用的就是分页中获取总记录条数
		Integer total = jt.queryForObject("select count(*) from account where money > ?",Integer.class,500);
	}

}