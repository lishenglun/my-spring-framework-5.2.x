package com.springstudy.msb.s_28.tx_annotation.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/6/16 5:04 下午
 */
@Component
public class BookDaoImpl implements BookDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Transactional
	@Override
	public void updateBalance(String userName, int money) {
		String sql = "update account set money=money-? where name = ?";
		jdbcTemplate.update(sql, money, userName);
	}

}