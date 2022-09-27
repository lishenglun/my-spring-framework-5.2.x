package com.springstudy.book2.chapter8_spring_jdbc;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/18 10:24 下午
 */
public interface UserService {

	public void save(User user);

	public List<User> getUsers();

}