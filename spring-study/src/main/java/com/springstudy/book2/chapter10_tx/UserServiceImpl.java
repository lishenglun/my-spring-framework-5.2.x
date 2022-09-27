package com.springstudy.book2.chapter10_tx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/18 10:25 下午
 */
public class UserServiceImpl implements UserService {

	private JdbcTemplate jdbcTemplate;

	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Transactional
	@Override
	public void save(User user) {
		jdbcTemplate.update("INSERT INTO `user` (`name`, `age`, `sex`) VALUES (?,?,?)",
				new Object[]{user.getName(), user.getAge(), user.getSex()},
				new int[]{Types.VARBINARY, Types.INTEGER, Types.VARBINARY});

		// 事务测试
		//throw new RuntimeException("aa");
	}

	@SuppressWarnings("inchecked")
	@Override
	public List<User> getUsers() {
		//return = jdbcTemplate.query("select * from user", new UserRowMapper());
		return jdbcTemplate.query("select * from user where age = ?", new Object[]{20}, new int[]{Types.INTEGER}, new UserRowMapper());
	}

}