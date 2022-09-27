package com.springstudy.service.impl;

import com.springstudy.dao.UserDao;
import com.springstudy.service.UserService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/29 10:41 下午
 */
@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private ApplicationContext applicationContext;

	private UserDao userDao;

	@Override
	public void invokeMethod(String choose) {
		if ("A".equals(choose)) {
			userDao = applicationContext.getBean("userDaoImplA", UserDao.class);
		} else if ("B".equals(choose)) {
			userDao = applicationContext.getBean("userDaoImplB", UserDao.class);
		}
		System.out.println(userDao);
	}

}