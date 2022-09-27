package com.springstudy.msb.s_27.tx_xml.dao;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/14 4:38 下午
 */
public class BookDaoImpl implements BookDao {

	private JdbcTemplate jdbcTemplate;

	@Override
	public void updateBalanceInDao(String userName, int money) {
		String sql = "update account set money=money-? where name = ?";
		jdbcTemplate.update(sql, money, userName);
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

}