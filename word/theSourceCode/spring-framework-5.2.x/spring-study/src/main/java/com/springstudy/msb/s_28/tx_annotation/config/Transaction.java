package com.springstudy.msb.s_28.tx_annotation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/6/16 5:04 下午
 */
@Configuration
@PropertySource("classpath:msb/spring-27-tx-dbconfig.properties")
@ComponentScan(value = "com.springstudy.msb.s_28.tx_annotation")
// 开启声明式事务
@EnableTransactionManagement
public class Transaction {

	@Value("${jdbc.driverClass}")
	private String driverClassname;

	@Value("${jdbc.url}")
	private String url;

	@Value("${jdbc.username}")
	private String username;

	@Value("${jdbc.password}")
	private String password;

	// 数据源
	@Bean
	public DataSource dataSource() {
		// spring内置的数据源
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverClassname);
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		return dataSource;
	}

	// 数据库的操作模版：用于基本的CRUD
	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	// 事务管理器
	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

}