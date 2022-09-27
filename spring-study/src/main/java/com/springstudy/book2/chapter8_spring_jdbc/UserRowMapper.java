package com.springstudy.book2.chapter8_spring_jdbc;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/18 10:22 下午
 */
public class UserRowMapper implements RowMapper<User> {

	@Override
	public User mapRow(ResultSet rs, int rowNum) throws SQLException {
		User user = new User();
		user.setId(rs.getInt("id"));
		user.setName(rs.getString("name"));
		user.setAge(rs.getInt("age"));
		user.setSex(rs.getString("sex"));

		return user;
	}

}